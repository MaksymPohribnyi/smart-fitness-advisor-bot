package com.ua.pohribnyi.fitadvisorbot.service.ai.resilience;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.google.genai.Client;
import com.google.genai.Models;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.ua.pohribnyi.fitadvisorbot.repository.ai.GenerationJobRepository;
import com.ua.pohribnyi.fitadvisorbot.service.ai.GeminiApiClient;
import com.ua.pohribnyi.fitadvisorbot.service.ai.GenerationJobUpdaterService;
import com.ua.pohribnyi.fitadvisorbot.service.ai.factory.GeminiConfigFactory;
import com.ua.pohribnyi.fitadvisorbot.service.ai.schema.GeminiSchemaDefiner;

import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration;
import io.github.resilience4j.springboot3.ratelimiter.autoconfigure.RateLimiterAutoConfiguration;
import io.github.resilience4j.springboot3.retry.autoconfigure.RetryAutoConfiguration;

/**
 * Unit tests for Retry pattern in GeminiApiClient.
 * 
 * Testing: - Retry on transient failures - Exponential backoff timing -
 * Exception ignore list
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {
    GeminiApiClient.class,
    GeminiConfigFactory.class,
    GeminiSchemaDefiner.class
})
@EnableConfigurationProperties
@ImportAutoConfiguration(classes = {
    AopAutoConfiguration.class,
    RateLimiterAutoConfiguration.class,
    CircuitBreakerAutoConfiguration.class,
    RetryAutoConfiguration.class
})
@TestPropertySource(properties = {
    // Retry: Primary focus
    "resilience4j.retry.instances.geminiApi.max-attempts=3",
    "resilience4j.retry.instances.geminiApi.wait-duration=50ms",
    "resilience4j.retry.instances.geminiApi.enable-exponential-backoff=true",
    "resilience4j.retry.instances.geminiApi.exponential-backoff-multiplier=2",
    "resilience4j.retry.instances.geminiApi.ignore-exceptions=java.lang.IllegalArgumentException",
    
    // RateLimiter: Disabled
    "resilience4j.ratelimiter.instances.geminiApi.limit-for-period=1000",
    "resilience4j.ratelimiter.instances.geminiApi.limit-refresh-period=1s",
    
    // CircuitBreaker: Disabled
    "resilience4j.circuitbreaker.instances.geminiApi.minimum-number-of-calls=1000",
    "resilience4j.circuitbreaker.instances.geminiApi.failure-rate-threshold=99",
    "resilience4j.circuitbreaker.instances.geminiApi.ignore-exceptions=java.lang.IllegalArgumentException",
    
    "google.gemini.api.key=fake-key"
})
class GeminiRetryTest {

	@Autowired
	private GeminiApiClient geminiApiClient;

	@MockitoBean
	private ApplicationEventPublisher eventPublisher;

	@MockitoBean
	private GenerationJobRepository jobRepository;

	@MockitoBean
	private GenerationJobUpdaterService jobUpdaterService;

	@MockitoBean
	private Client mockGeminiClient;

	private Models mockModels;

	@MockitoBean
	private GenerateContentConfig mockConfig;

	@Autowired
    private RetryRegistry retryRegistry;
	
	@BeforeEach
	void setUp() {
		Mockito.reset(mockGeminiClient);

		mockModels = mock(Models.class);
		ReflectionTestUtils.setField(mockGeminiClient, "models", mockModels);
		
		
		RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(3)
                .intervalFunction(IntervalFunction.ofExponentialBackoff(Duration.ofMillis(50), 2.0))
                .ignoreExceptions(IllegalArgumentException.class) 
                .build();
        retryRegistry.remove("geminiApi");
        retryRegistry.retry("geminiApi", retryConfig);
		
	}

	@Test
	@DisplayName("Retry attempts 3 times on RuntimeException")
	void retry_attemptsThreeTimesOnRuntimeException() {
		// Arrange: Fail twice, then succeed
		GenerateContentResponse mockSuccessResponse = mockSuccessResponse();
		when(mockModels.generateContent(anyString(), anyList(), any())).thenThrow(new RuntimeException("Timeout 1"))
				.thenThrow(new RuntimeException("Timeout 2")).thenReturn(mockSuccessResponse);

		// Act
		String result = geminiApiClient.callGeminiApi("test");

		// Assert
		assertThat(result).isNotNull();
		verify(mockModels, times(3)).generateContent(anyString(), anyList(), any());
	}

	@Test
	@DisplayName("Retry does NOT retry on IllegalArgumentException")
	void retry_doesNotRetryOnIllegalArgumentException() {
		// Arrange
		when(mockModels.generateContent(anyString(), anyList(), any()))
				.thenThrow(new IllegalArgumentException("Invalid prompt"));

		// Act & Assert
		try {
			geminiApiClient.callGeminiApi("test");
		} catch (Exception e) {
			assertThat(e).isInstanceOf(RuntimeException.class);
		}

		// Verify: Called only once (no retry)
		verify(mockModels, times(1)).generateContent(anyString(), anyList(), any());
	}

	@Test
	@DisplayName("Retry uses exponential backoff (50ms, 100ms)")
	void retry_usesExponentialBackoff() {
		// Arrange: Fail twice, then succeed
		GenerateContentResponse mockSuccessResponse = mockSuccessResponse();
		when(mockModels.generateContent(anyString(), anyList(), any())).thenThrow(new RuntimeException("Timeout 1"))
				.thenThrow(new RuntimeException("Timeout 2")).thenReturn(mockSuccessResponse);

		// Act
		long startTime = System.currentTimeMillis();
		geminiApiClient.callGeminiApi("test");
		long duration = System.currentTimeMillis() - startTime;

		// Assert: Total wait time = 50ms + 100ms = 150ms
		assertThat(duration).isBetween(140L, 250L); // Allow some overhead
		verify(mockModels, times(3)).generateContent(anyString(), anyList(), any());
	}

	@Test
	@DisplayName("Retry eventually fails after max attempts")
	void retry_failsAfterMaxAttempts() {
		// Arrange: Always fail
		when(mockModels.generateContent(anyString(), anyList(), any()))
				.thenThrow(new RuntimeException("Persistent failure"));

		// Act & Assert
		try {
			geminiApiClient.callGeminiApi("test");
		} catch (Exception e) {
			assertThat(e).hasMessageContaining("unavailable");
		}

		// Verify: 3 attempts made
		verify(mockModels, times(3)).generateContent(anyString(), anyList(), any());
	}

	@Test
	@DisplayName("Retry succeeds on first attempt if no failure")
	void retry_succeedsImmediatelyIfNoFailure() {
		// Arrange
		GenerateContentResponse mockSuccessResponse = mockSuccessResponse();
		when(mockModels.generateContent(anyString(), anyList(), any())).thenReturn(mockSuccessResponse);

		// Act
		long startTime = System.currentTimeMillis();
		String result = geminiApiClient.callGeminiApi("test");
		long duration = System.currentTimeMillis() - startTime;

		// Assert: No retry delay
		assertThat(result).isNotNull();
		assertThat(duration).isLessThan(50L); // No wait time
		verify(mockModels, times(1)).generateContent(anyString(), anyList(), any());
	}

	private GenerateContentResponse mockSuccessResponse() {
		GenerateContentResponse response = mock(GenerateContentResponse.class);
		when(response.text()).thenReturn("{\"dailyMetrics\": [], \"activities\": []}");
		return response;
	}
}