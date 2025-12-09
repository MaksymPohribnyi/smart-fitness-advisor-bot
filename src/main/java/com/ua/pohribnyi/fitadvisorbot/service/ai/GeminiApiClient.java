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
import com.ua.pohribnyi.fitadvisorbot.enums.JobStatus;
import com.ua.pohribnyi.fitadvisorbot.service.ai.factory.GeminiConfigFactory;
import com.ua.pohribnyi.fitadvisorbot.service.ai.schema.GeminiSchemaDefiner;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class GeminiApiClient {

	private final GenerationJobUpdaterService jobUpdaterService; 
	private final Client geminiClient;
	private final GeminiConfigFactory configFactory; 
    private final GeminiSchemaDefiner schemaDefiner;

	private static final String MODEL_NAME = "gemini-2.5-flash-lite";

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
			String rawResponse = callGeminiApi(prompt);
			log.info("[{}] Received response for job {}, length: {} chars", threadName, jobId, rawResponse.length());

			// Step 3: Clean and validate JSON
			String cleanedJson = cleanupAndValidateJson(rawResponse);
			log.debug("[{}] JSON cleaned and validated for job {}", threadName, jobId);

			// Step 4: Stage response in DB (separate TX)
			jobUpdaterService.stageJobResponse(jobId, cleanedJson);

			log.info("[{}] Job {} successfully downloaded and staged", threadName, jobId);

		} catch (Exception e) {
			log.error("[{}] Job {} failed: {}", threadName, jobId, e.getMessage());
			jobUpdaterService.markJobAsFailed(jobId, e);
		}
	}

	/**
	 * Makes actual API call to Gemini.
	 * 
     * Порядок застосування:
     * 1. @RateLimiter → Чекає до 30s на доступний слот
     * 2. @CircuitBreaker → Блокує якщо API недоступний
     * 3. @Retry → 3 спроби з exponential backoff
     */
    @RateLimiter(name = "geminiApi")
	@CircuitBreaker(name = "geminiApi", fallbackMethod = "apiFallback")
	@Retry(name = "geminiApi")
	public String callGeminiApi(String prompt) {
		
    	log.debug("Calling Gemini API...");

		Content content = Content.builder()
				.role("user")
				.parts(Part.builder().text(prompt).build())
				.build();

		GenerateContentConfig config = configFactory.createStructuredConfig(schemaDefiner.getFitnessHistorySchema());
		GenerateContentResponse response = geminiClient.models.generateContent(MODEL_NAME, List.of(content), config);

		String text = extractResponseText(response);

		if (text == null || text.isBlank()) {
			log.error("Empty response from Gemini. Response object: {}", response);
			throw new IllegalStateException("Empty response from Gemini API");
		}

		return text;
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
     * Fallback if Circuit Breaker open.
     */
    private String apiFallback(String prompt, Exception e) {
        log.error("API unavailable, fallback triggered: {}", e.getMessage());
        throw new RuntimeException("Gemini API temporarily unavailable. Please try again later.", e);
    }

}