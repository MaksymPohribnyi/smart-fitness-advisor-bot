package com.ua.pohribnyi.fitadvisorbot.service.ai;

import java.util.List;
import java.util.Optional;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.google.genai.Client;
import com.google.genai.types.Candidate;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import com.ua.pohribnyi.fitadvisorbot.model.enums.JobStatus;
import com.ua.pohribnyi.fitadvisorbot.util.retryable.RetryHelper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class GeminiApiClient {

	private final GenerationJobUpdaterService jobUpdaterService; 
	private final Client geminiClient;
	private final GenerateContentConfig geminiGenerationConfig;

	private static final String MODEL_NAME = "gemini-2.5-flash-lite";

	private static final int MAX_RETRY_ATTEMPTS = 3;
	private static final long INITIAL_RETRY_DELAY_MS = 2000; // 2 seconds

	/**
	 * Async entry point for AI generation. Executed in dedicated thread pool
	 * (aiGenerationExecutor).
	 * 
	 * @param jobId  Database ID of the generation job
	 * @param prompt Formatted prompt for Gemini API
	 */
	@Async("aiGenerationExecutor")
	public void generateAndStageHistory(Long jobId, String prompt) {
		String threadName = Thread.currentThread().getName();
		log.info("[Thread: {}] Starting generation for job {}", threadName, jobId);

		try {
			// Step 1: Mark as DOWNLOADING (separate TX)
			jobUpdaterService.updateJobStatus(jobId, JobStatus.DOWNLOADING);

			// Step 2: Call API with retry logic (no TX, can be slow)
			String rawResponse = callGeminiApiWithRetry(prompt);
			log.info("[{}] Received response for job {}, length: {} chars", threadName, jobId, rawResponse.length());

			// Step 3: Clean and validate JSON
			String cleanedJson = cleanupAndValidateJson(rawResponse);
			log.debug("[{}] JSON cleaned and validated for job {}", threadName, jobId);

			// Step 4: Stage response in DB (separate TX)
			jobUpdaterService.stageJobResponse(jobId, cleanedJson);

			log.info("[{}] Job {} successfully downloaded and staged", threadName, jobId);

		} catch (Exception e) {
			log.error("Generation failed for job {}: {}", jobId, e.getMessage(), e);
			jobUpdaterService.markJobAsFailed(jobId, e);
		}
	}

	/**
	 * Calls Gemini API with retry logic. Retries on rate limit (429) or transient
	 * errors (5xx).
	 */
	private String callGeminiApiWithRetry(String prompt) {
		return RetryHelper.execute(() -> callGeminiApi(prompt), // What to do
				this::isRetryableError, // When to retry
				MAX_RETRY_ATTEMPTS, // How many times (3)
				INITIAL_RETRY_DELAY_MS // How long to wait (2s)
		);
	}

	/**
	 * Makes actual API call to Gemini.
	 * 
	 * @throws RuntimeException on API errors
	 */
	private String callGeminiApi(String prompt) {
		try {
			Content content = Content.builder().role("user").parts(Part.builder().text(prompt).build()).build();

			GenerateContentResponse response = geminiClient.models.generateContent(MODEL_NAME, List.of(content),
					geminiGenerationConfig);

			String text = extractResponseText(response);

			if (text == null || text.isBlank()) {
				log.error("Empty response from Gemini. Response object: {}", response);
				throw new RuntimeException("Empty response from Gemini API");
			}

			return text;

		} catch (Exception e) {
			// Wrap all exceptions for consistent handling
			log.error("Gemini API call failed: {}", e.getMessage());
			throw new RuntimeException("Gemini API error: " + e.getMessage(), e);
		}
	}

	/**
	 * Extracts text from response, handling different response formats.
	 */
	private String extractResponseText(GenerateContentResponse response) {
		// Primary method: direct text() call
		if (response.text() != null && !response.text().isBlank()) {
			return response.text();
		}

		// Fallback: extract from candidates structure
		// candidates() returns Optional<List<Candidate>>
		if (response.candidates().isEmpty()) {
			log.warn("No candidates in Gemini response");
			return null;
		}

		List<Candidate> candidates = response.candidates().get();
		if (candidates.isEmpty()) {
			log.warn("Empty candidates list in Gemini response");
			return null;
		}

		Candidate firstCandidate = candidates.get(0);
		if (firstCandidate.content() == null) {
			log.warn("No content in first candidate");
			return null;
		}

		Optional<Content> content = firstCandidate.content();
		if (content.isEmpty() || content.get().parts() == null || content.get().parts().isEmpty()) {
			log.warn("No parts in candidate content");
			return null;
		}

		// Find first non-empty text part
		return content.get().parts()
				.stream()
				.flatMap(List::stream)
				.map(Part::text)
				.flatMap(Optional::stream) 
				.filter(text -> !text.isBlank())
				.findFirst()
				.orElse(null);
	}

	/**
	 * Cleans Gemini response: extracts JSON, validates structure.
	 */
	private String cleanupAndValidateJson(String response) {
		// Remove markdown code blocks if present
		String cleaned = response.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();

		// Find JSON object boundaries
		int firstBrace = cleaned.indexOf('{');
		int lastBrace = cleaned.lastIndexOf('}');

		if (firstBrace == -1 || lastBrace == -1 || lastBrace < firstBrace) {
			throw new IllegalStateException("Invalid JSON in Gemini response. First 200 chars: "
					+ cleaned.substring(0, Math.min(200, cleaned.length())));
		}

		String json = cleaned.substring(firstBrace, lastBrace + 1);

		// Basic validation: check for required keys
		if (!json.contains("\"dailyMetrics\"") || !json.contains("\"activities\"")) {
			throw new IllegalStateException("Missing required keys in JSON response: dailyMetrics or activities");
		}

		return json;
	}

	/**
	 * Determines if error is retryable (rate limit, network issues).
	 */
	private boolean isRetryableError(Exception e) {
		String message = e.getMessage();
		if (message == null)
			return false;

		return message.contains("429") || // Rate limit
				message.contains("503") || // Service unavailable
				message.contains("timeout") || message.contains("connection");
	}

}