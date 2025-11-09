package com.ua.pohribnyi.fitadvisorbot.util.exception;

/**
 * Exception thrown when Gemini API call fails with a retryable error. Used
 * by @Retryable mechanism in GeminiApiClient.
 */
public class GeminiApiException extends RuntimeException {

	public GeminiApiException(String message) {
		super(message);
	}

	public GeminiApiException(String message, Throwable cause) {
		super(message, cause);
	}
}