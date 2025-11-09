package com.ua.pohribnyi.fitadvisorbot.service.ai;

import java.util.List;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ua.pohribnyi.fitadvisorbot.event.JobDownloadedEvent;
import com.ua.pohribnyi.fitadvisorbot.model.dto.ParsedData;
import com.ua.pohribnyi.fitadvisorbot.model.dto.google.ActivityDto;
import com.ua.pohribnyi.fitadvisorbot.model.dto.google.DailyMetricDto;
import com.ua.pohribnyi.fitadvisorbot.model.entity.Activity;
import com.ua.pohribnyi.fitadvisorbot.model.entity.DailyMetric;
import com.ua.pohribnyi.fitadvisorbot.model.entity.GenerationJob;
import com.ua.pohribnyi.fitadvisorbot.model.enums.JobStatus;
import com.ua.pohribnyi.fitadvisorbot.repository.ai.GenerationJobRepository;
import com.ua.pohribnyi.fitadvisorbot.repository.data.ActivityRepository;
import com.ua.pohribnyi.fitadvisorbot.repository.data.DailyMetricRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class SyntheticDataProcessorService {

	private final GenerationJobRepository jobRepository;
	private final ActivityRepository activityRepository;
	private final DailyMetricRepository dailyMetricRepository;
	private final ObjectMapper objectMapper;

	private static final int MAX_ERROR_DETAILS_LENGTH = 10000;

	/**
	 * Listens for JobDownloadedEvent AFTER Worker 1's transaction commits.
	 * 
	 * TransactionPhase.AFTER_COMMIT guarantees: - job.rawResponse is persisted in
	 * DB - No race conditions with Worker 1
	 * 
	 * @Async: Runs in separate thread (dataProcessingExecutor)
	 */
	@Async("dataProcessingExecutor")
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void handleJobDownloadedEvent(JobDownloadedEvent event) {
		Long jobId = event.getJobId();
		String threadName = Thread.currentThread().getName();

		log.info("[Thread: {}] Received event for job {}", threadName, jobId);

		try {
			processJobInTransaction(jobId);
		} catch (Exception e) {
			log.error("[{}] Failed to process job {}", threadName, jobId);
		}
	}

	/**
	 * Processes job in a single, atomic transaction.
	 * 
	 * On failure: - Mark job as FAILED - Rollback all DB inserts
	 * 
	 * @param jobId Database ID of the job
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 30)
	private void processJobInTransaction(Long jobId) {

		String threadName = Thread.currentThread().getName();

		// Step 1: Load job with rawResponse
		GenerationJob job = jobRepository.findById(jobId)
				.orElseThrow(() -> new IllegalStateException("Job not found: " + jobId));

		// Step 2: Validate job state
		if (!job.isProcessable()) {
			log.warn("[{}] Job {} is not processable. Status: {}, hasResponse: {}", threadName, jobId, job.getStatus(),
					job.getRawResponse() != null);
			return;
		}

		// Step 3: Mark as PROCESSING
		job.setStatus(JobStatus.PROCESSING);
		jobRepository.save(job);
		log.debug("[{}] Job {} marked as PROCESSING", threadName, jobId);

		try {
			// Step 4: Parse JSON
			String rawJson = job.getRawResponse();
			ParsedData parsed = parseJson(rawJson);

			log.info("[{}] Parsed {} metrics and {} activities for job {}", threadName, parsed.metricDtos().size(),
					parsed.activityDtos().size(), jobId);

			// Step 5: Map to entities
			List<DailyMetric> metrics = parsed.metricDtos().stream()
					.map(dto -> DailyMetricDto.mapToEntity(dto, job.getUser())).toList();

			List<Activity> activities = parsed.activityDtos().stream()
					.map(dto -> ActivityDto.mapToEntity(dto, job.getUser())).toList();

			// Step 6: Batch insert (uses hibernate.jdbc.batch_size from application.yml)
			dailyMetricRepository.saveAll(metrics);
			activityRepository.saveAll(activities);

			log.debug("[{}] Batch inserted {} metrics and {} activities", threadName, metrics.size(),
					activities.size());

			// Step 7: Mark as PROCESSED and clear rawResponse
			job.markAsProcessed();
			jobRepository.save(job);

			log.info("[{}] Job {} processed successfully", threadName, jobId);

		} catch (Exception e) {
			log.error("[{}] Processing failed for job {}: {}", threadName, jobId, e.getMessage(), e);

			// Mark as failed with structured error
			String errorCode = categorizeError(e);
			String shortMessage = extractShortMessage(e.getMessage());
			String fullDetails = buildErrorDetails(e);

			job.markAsFailed(errorCode, shortMessage, fullDetails);
			jobRepository.save(job);

			// Rethrow to trigger transaction rollback
			throw new RuntimeException("Failed to process job " + jobId, e);
		}
	}

	/**
	 * Parses raw JSON into DTOs.
	 * 
	 * @throws Exception if JSON invalid or missing required fields
	 */
	private ParsedData parseJson(String rawJson) {
		try {
			JsonNode rootNode = objectMapper.readTree(rawJson);

			// Validate required keys exist
			if (!rootNode.has("dailyMetrics")) {
				throw new IllegalArgumentException("Missing 'dailyMetrics' in JSON");
			}
			if (!rootNode.has("activities")) {
				throw new IllegalArgumentException("Missing 'activities' in JSON");
			}

			List<DailyMetricDto> metricDtos = objectMapper.convertValue(rootNode.get("dailyMetrics"),
					new TypeReference<>() {
					});

			List<ActivityDto> activityDtos = objectMapper.convertValue(rootNode.get("activities"),
					new TypeReference<>() {
					});

			// Validate counts
			if (metricDtos.isEmpty()) {
				throw new IllegalArgumentException("dailyMetrics array is empty");
			}
			if (activityDtos.isEmpty()) {
				log.warn("activities array is empty (acceptable but unusual)");
			}

			return new ParsedData(metricDtos, activityDtos);

		} catch (Exception e) {
			log.error("JSON parsing failed: {}", e.getMessage());
			throw new RuntimeException("Invalid JSON structure: " + e.getMessage(), e);
		}
	}

	/**
	 * Categorizes processing errors for monitoring.
	 * 
	 * Categories: - JSON_PARSE_ERROR: Jackson parsing failed -
	 * JSON_VALIDATION_ERROR: Missing/invalid fields - DB_INSERT_ERROR: Database
	 * constraint violations - PROCESSING_ERROR: Generic processing errors
	 */
	private String categorizeError(Exception exception) {
		if (exception == null)
			return "UNKNOWN_ERROR";

		String message = exception.getMessage();
		if (message == null)
			return "PROCESSING_ERROR";

		String lowerMsg = message.toLowerCase();

		// JSON parsing errors
		if (exception instanceof com.fasterxml.jackson.core.JsonProcessingException || lowerMsg.contains("json")
				|| lowerMsg.contains("parse")) {
			return "JSON_PARSE_ERROR";
		}

		// Validation errors
		if (exception instanceof IllegalArgumentException || lowerMsg.contains("missing")
				|| lowerMsg.contains("invalid")) {
			return "JSON_VALIDATION_ERROR";
		}

		// Database errors
		if (lowerMsg.contains("constraint") || lowerMsg.contains("duplicate") || lowerMsg.contains("foreign key")
				|| lowerMsg.contains("sql")) {
			return "DB_INSERT_ERROR";
		}

		return "PROCESSING_ERROR";
	}

	/**
	 * Extracts first meaningful line for UI display.
	 */
	private String extractShortMessage(String errorMessage) {
		if (errorMessage == null || errorMessage.isBlank()) {
			return "Processing failed";
		}

		String[] lines = errorMessage.split("\n");
		String firstLine = lines[0].trim();

		if (firstLine.length() > 200) {
			return firstLine.substring(0, 197) + "...";
		}

		return firstLine;
	}

	/**
	 * Builds detailed error information for debugging.
	 */
	private String buildErrorDetails(Exception exception) {
		if (exception == null)
			return null;

		StringBuilder details = new StringBuilder();

		details.append("Exception: ").append(exception.getClass().getSimpleName()).append("\n");
		details.append("Message: ").append(exception.getMessage()).append("\n\n");

		details.append("Stack trace:\n");
		StackTraceElement[] stackTrace = exception.getStackTrace();
		int maxElements = Math.min(10, stackTrace.length);

		for (int i = 0; i < maxElements; i++) {
			details.append("  at ").append(stackTrace[i].toString()).append("\n");
		}

		if (stackTrace.length > maxElements) {
			details.append("  ... ").append(stackTrace.length - maxElements).append(" more frames\n");
		}

		Throwable cause = exception.getCause();
		if (cause != null) {
			details.append("\nCaused by: ").append(cause.getClass().getSimpleName()).append(": ")
					.append(cause.getMessage()).append("\n");
		}

		String result = details.toString();
		if (result.length() > MAX_ERROR_DETAILS_LENGTH) {
			return result.substring(0, MAX_ERROR_DETAILS_LENGTH - 50) + "\n\n... (truncated, full trace in logs)";
		}

		return result;
	}

}