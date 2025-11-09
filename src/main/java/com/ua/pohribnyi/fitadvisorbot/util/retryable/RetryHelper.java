package com.ua.pohribnyi.fitadvisorbot.util.retryable;

import lombok.extern.slf4j.Slf4j;

/**
 * Simple, reusable retry utility without external dependencies.
 * 
 * Design principles: - Simple to understand (no magic) - Easy to debug
 * (explicit logging) - Reusable across entire project - Zero external
 * dependencies
 * 
 * Usage example:
 * 
 * <pre>
 * String response = RetryHelper.execute(
 * 		() -> callGeminiApi(prompt), 
 * 		e -> e.getMessage().contains("429"), 
 * 		3, // max
 * 		2000 // initial delay (2s));
 * </pre>
 */
@Slf4j
public class RetryHelper {

	private RetryHelper() {
		// Utility class
	}

	/**
	 * Executes operation with exponential backoff retry.
	 * 
	 * @param operation      The operation to execute
	 * @param isRetryable    Predicate to determine if error should trigger retry
	 * @param maxAttempts    Maximum number of attempts (recommended: 3)
	 * @param initialDelayMs Initial delay between retries (recommended: 2000ms)
	 * @param <T>            Return type of operation
	 * @return Result of successful operation
	 * @throws RuntimeException if all retries exhausted
	 */
	public static <T> T execute(Operation<T> operation, ErrorPredicate isRetryable, int maxAttempts,
			long initialDelayMs) {
		Exception lastException = null;
		long delayMs = initialDelayMs;

		for (int attempt = 1; attempt <= maxAttempts; attempt++) {
			try {
				log.debug("Executing operation, attempt {}/{}", attempt, maxAttempts);
				T result = operation.execute();

				if (attempt > 1) {
					log.info("Operation succeeded on attempt {}/{}", attempt, maxAttempts);
				}

				return result;

			} catch (Exception e) {
				lastException = e;

				// Last attempt - no more retries
				if (attempt == maxAttempts) {
					log.error("Operation failed after {} attempts: {}", maxAttempts, e.getMessage());
					break;
				}

				// Check if error is retryable
				if (isRetryable != null && !isRetryable.test(e)) {
					log.error("Non-retryable error detected, aborting: {}", e.getMessage());
					break;
				}

				// Log and wait before retry
				log.warn("Attempt {}/{} failed: {}. Retrying in {}ms...", attempt, maxAttempts, e.getMessage(),
						delayMs);

				sleep(delayMs);
				delayMs *= 2; // Exponential backoff
			}
		}

		// All retries failed
		throw new RuntimeException("Operation failed after " + maxAttempts + " attempts", lastException);
	}

	/**
	 * Simplified version with default retry predicate (retries all exceptions).
	 */
	public static <T> T execute(Operation<T> operation, int maxAttempts, long initialDelayMs) {
		return execute(operation, e -> true, maxAttempts, initialDelayMs);
	}

	/**
	 * Thread sleep with proper InterruptedException handling.
	 */
	private static void sleep(long milliseconds) {
		try {
			Thread.sleep(milliseconds);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			log.warn("Retry sleep interrupted");
			throw new RuntimeException("Interrupted during retry", e);
		}
	}
}