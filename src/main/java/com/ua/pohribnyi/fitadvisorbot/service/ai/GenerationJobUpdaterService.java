package com.ua.pohribnyi.fitadvisorbot.service.ai;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ua.pohribnyi.fitadvisorbot.enums.JobStatus;
import com.ua.pohribnyi.fitadvisorbot.model.entity.GenerationJob;
import com.ua.pohribnyi.fitadvisorbot.repository.ai.GenerationJobRepository;
import com.ua.pohribnyi.fitadvisorbot.util.concurrency.event.JobDownloadedEvent;

import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service dedicated to updating GenerationJob entities in separate
 * transactions. This avoids Spring AOP self-invocation issues when called from
 * an @Async method.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class GenerationJobUpdaterService {

	private final GenerationJobRepository jobRepository;
	private final ApplicationEventPublisher eventPublisher;

	private static final int MAX_ERROR_DETAILS_LENGTH = 10000; // 10KB

	/**
	 * Updates job status in a separate, short transaction.
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 5)
	@Retry(name = "dbRead")
	public void updateJobStatus(Long jobId, JobStatus status) {
		GenerationJob job = jobRepository.findById(jobId)
				.orElseThrow(() -> new IllegalStateException("Job not found: " + jobId));
		job.setStatus(status);
		jobRepository.save(job);
	}

	/**
	 * Stages the cleaned response in a separate transaction.
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 10)
	@Retry(name = "dbRead")
	public void stageJobResponse(Long jobId, String cleanedJson) {
		GenerationJob job = jobRepository.findById(jobId)
				.orElseThrow(() -> new IllegalStateException("Job not found: " + jobId));
		// Step 1: Save data
		job.stageResponse(cleanedJson);
		// Step 2: Publish event (after TX commits)
		eventPublisher.publishEvent(new JobDownloadedEvent(this, jobId));
	}

	/**
	 * Marks job as failed in a separate transaction.
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 5)
	public void markJobAsFailed(Long jobId, Exception exception) {
		GenerationJob job = jobRepository.findById(jobId)
				.orElseThrow(() -> new IllegalStateException("Job not found: " + jobId));
		// Determine error code for monitoring
		String errorCode = categorizeError(exception);
		String shortMessage = extractShortMessage(exception.getMessage());
		String fullDetails = buildErrorDetails(exception);

		job.markAsFailed(errorCode, shortMessage, fullDetails);
		jobRepository.save(job);
	}

	/**
	 * Categorizes error for monitoring dashboards.
	 */
	private String categorizeError(Exception exception) {
		if (exception == null)
			return "UNKNOWN_ERROR";

		String message = exception.getMessage();
		if (message == null)
			return "UNKNOWN_ERROR";

		String lowerMsg = message.toLowerCase();

		if (lowerMsg.contains("429") || lowerMsg.contains("rate limit")) {
			return "API_RATE_LIMIT";
		}
		if (lowerMsg.contains("timeout")) {
			return "API_TIMEOUT";
		}
		if (lowerMsg.contains("json") || lowerMsg.contains("parse") || exception instanceof IllegalStateException) {
			return "INVALID_RESPONSE";
		}
		if (lowerMsg.contains("connection") || lowerMsg.contains("socket") || lowerMsg.contains("network")) {
			return "NETWORK_ERROR";
		}
		if (lowerMsg.contains("401") || lowerMsg.contains("403") || lowerMsg.contains("unauthorized")) {
			return "API_AUTH_ERROR";
		}
		if (lowerMsg.contains("gemini") || lowerMsg.contains("api")) {
			return "API_ERROR";
		}

		return "UNKNOWN_ERROR";
	}

	/**
	 * Extracts first meaningful line for short error message.
	 */
	private String extractShortMessage(String errorMessage) {
		if (errorMessage == null)
			return "Unknown error";

		// Take first line or first 200 chars
		String[] lines = errorMessage.split("\n");
		String firstLine = lines[0];

		return firstLine.length() > 200 ? firstLine.substring(0, 197) + "..." : firstLine;
	}

	/**
	 * Builds full error details including stack trace. Limited to
	 * MAX_ERROR_DETAILS_LENGTH.
	 */
	private String buildErrorDetails(Exception exception) {
		if (exception == null)
			return null;

		StringBuilder details = new StringBuilder();

		// Exception message
		details.append("Exception: ").append(exception.getClass().getSimpleName()).append("\n");
		details.append("Message: ").append(exception.getMessage()).append("\n\n");

		// Stack trace (limited)
		details.append("Stack trace:\n");
		StackTraceElement[] stackTrace = exception.getStackTrace();
		int maxElements = Math.min(10, stackTrace.length); // First 10 frames

		for (int i = 0; i < maxElements; i++) {
			details.append("  at ").append(stackTrace[i].toString()).append("\n");
		}

		if (stackTrace.length > maxElements) {
			details.append("  ... ").append(stackTrace.length - maxElements).append(" more frames\n");
		}

		// Caused by (if exists)
		Throwable cause = exception.getCause();
		if (cause != null) {
			details.append("\nCaused by: ").append(cause.getClass().getSimpleName()).append(": ")
					.append(cause.getMessage()).append("\n");
		}

		// Truncate if too long
		String result = details.toString();
		if (result.length() > MAX_ERROR_DETAILS_LENGTH) {
			return result.substring(0, MAX_ERROR_DETAILS_LENGTH - 50)
					+ "\n\n... (truncated, full trace available in logs)";
		}

		return result;
	}

}