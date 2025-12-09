package com.ua.pohribnyi.fitadvisorbot.service.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
import com.ua.pohribnyi.fitadvisorbot.enums.JobStatus;
import com.ua.pohribnyi.fitadvisorbot.repository.ai.GenerationJobRepository;
import com.ua.pohribnyi.fitadvisorbot.service.ai.factory.GeminiConfigFactory;
import com.ua.pohribnyi.fitadvisorbot.service.ai.schema.GeminiSchemaDefiner;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration;
import io.github.resilience4j.springboot3.ratelimiter.autoconfigure.RateLimiterAutoConfiguration;
import io.github.resilience4j.springboot3.retry.autoconfigure.RetryAutoConfiguration;

/**
 * Integration tests for Resilience4j patterns within GeminiApiClient.
 * * Testing Scope:
 * 1. Rate Limiter functionality
 * 2. Circuit Breaker state transitions
 * 3. Retry logic
 * 4. High concurrency load simulation
 * * Architecture:
 * - Uses Context Slicing (loads only necessary beans).
 * - Mocks Database interactions (UpdaterService) to avoid transaction issues.
 * - Manually injects Google GenAI mocks for final classes.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {
		GeminiApiClient.class, 
		GeminiConfigFactory.class, 
		GeminiSchemaDefiner.class
})
@EnableConfigurationProperties
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ImportAutoConfiguration(classes = { 
		AopAutoConfiguration.class,
		RateLimiterAutoConfiguration.class, 
		CircuitBreakerAutoConfiguration.class, 
		RetryAutoConfiguration.class})
@TestPropertySource(properties = {
		// Resilience4j config
		"resilience4j.ratelimiter.instances.geminiApi.limit-for-period=3",
	    "resilience4j.ratelimiter.instances.geminiApi.limit-refresh-period=10s",
	    "resilience4j.ratelimiter.instances.geminiApi.timeout-duration=100ms",
	    
	    "resilience4j.circuitbreaker.instances.geminiApi.sliding-window-size=3",
	    "resilience4j.circuitbreaker.instances.geminiApi.minimum-number-of-calls=3",
	    "resilience4j.circuitbreaker.instances.geminiApi.failure-rate-threshold=50",
	    "resilience4j.circuitbreaker.instances.geminiApi.wait-duration-in-open-state=5s",
	    "resilience4j.circuitbreaker.instances.geminiApi.permitted-number-of-calls-in-half-open-state=1",
	    
	    "resilience4j.retry.instances.geminiApi.max-attempts=3",
	    "resilience4j.retry.instances.geminiApi.wait-duration=50ms",
	    "resilience4j.retry.instances.geminiApi.enable-exponential-backoff=true",
	    "resilience4j.retry.instances.geminiApi.exponential-backoff-multiplier=2",
	    "resilience4j.retry.instances.geminiApi.ignore-exceptions=java.lang.IllegalArgumentException",

		"google.gemini.api.key=fake-key" 
})
class GeminiApiClientResilienceTest {

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
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeEach
    void setUp() {
    	mockModels = mock(Models.class);
        ReflectionTestUtils.setField(mockGeminiClient, "models", mockModels);
        
        circuitBreakerRegistry.circuitBreaker("geminiApi").transitionToClosedState();
    }

    /**
     * Test #1: Verify Rate Limiter allows requests within the limit.
     */
    @Test
    @DisplayName("Rate Limiter allows first 3 requests")
    void rateLimiter_allowsThreeRequestsPerPeriod() throws Exception {
        // Arrange
        mockSuccessfulApiCall();
        CountDownLatch latch = new CountDownLatch(3);
        AtomicInteger successCount = new AtomicInteger(0);

        // Act: Запускаємо 3 запити одночасно
		for (int i = 0; i < 3; i++) {
			new Thread(() -> {
				try {
					geminiApiClient.callGeminiApi("test prompt");
					successCount.incrementAndGet();
				} catch (Exception e) {
					// ignore
				} finally {
					latch.countDown();
				}
			}).start();
		}

        // Assert
        boolean completed = latch.await(5, TimeUnit.SECONDS);

        assertThat(completed).isTrue();
        assertThat(successCount.get()).isEqualTo(3);
        
        verify(mockModels, times(3)).generateContent(anyString(), anyList(), any());
    }

	/**
	 * Test #2: Verify Rate Limiter blocks the 4th request (limit=3).
	 */
    @Test
    @DisplayName("Rate Limiter blocks the 4th request")
    void rateLimiter_blocksRequestsAfterLimit() throws Exception {
        // Arrange
        mockSuccessfulApiCall();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger blockedCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(4);

        // Act: Fire 4 requests
        for (int i = 0; i < 4; i++) {
            new Thread(() -> {
                try {
                    geminiApiClient.callGeminiApi("test");
                    Thread.sleep(50);
                    successCount.incrementAndGet();
                } catch (Exception e) {
					String msg = e.getMessage().toLowerCase();
					if (msg.contains("rate") || msg.contains("permit") || msg.contains("unavailable")) {
						blockedCount.incrementAndGet();
					}
                } finally {
                    latch.countDown();
                }
            }).start();
        }

        latch.await(5, TimeUnit.SECONDS);

        assertThat(successCount.get()).as("Should allow 3 requests").isEqualTo(3);
        assertThat(blockedCount.get()).as("Should block 1 request").isGreaterThanOrEqualTo(1);
    }

    /**
     * Test #3: Verify Circuit Breaker opens after failures.
     */
    @Test
    @DisplayName("Circuit Breaker opens after 50% failure threshold")
    void circuitBreaker_opensAfterFailureThreshold() throws Exception {
    	// Arrange: API always fails
        when(mockModels.generateContent(anyString(), anyList(), any()))
            .thenThrow(new RuntimeException("API Error"));

        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("geminiApi");

		// Act: Call 3 times (min number of calls)
        for (int i = 0; i < 3; i++) {
            try {
                geminiApiClient.callGeminiApi("test");
            } catch (Exception e) {
                // Expected
            }
        }
		// Assert: State should shift to OPEN
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    /**
     * Test #4: Verify Circuit Breaker blocks calls instantly when OPEN.
     */
    @Test
    @DisplayName("Circuit Breaker blocks requests when OPEN")
    void circuitBreaker_blocksRequestsWhenOpen() throws Exception {
    	// Arrange: Force OPEN state
        circuitBreakerRegistry.circuitBreaker("geminiApi").transitionToOpenState();

        // Act & Assert
        try {
            geminiApiClient.callGeminiApi("test");
        } catch (Exception e) {
            assertThat(e.getMessage()).contains("unavailable");
        }

		// Verify: Actual API was NEVER called
		verify(mockModels, never()).generateContent(anyString(), anyList(), any());
	}

    /**
     * Test #5: Verify Retry logic (3 attempts on failure).
     */
    @Test
    @DisplayName("Retry attempts 3 times on RuntimeException")
    void retry_attemptsThreeTimesOnRuntimeException() {
        // Arrange:
        when(mockModels.generateContent(anyString(), anyList(), any()))
            .thenThrow(new RuntimeException("Connection timeout"))
            .thenThrow(new RuntimeException("Connection timeout"))
            .thenReturn(mockSuccessResponse());

        // Act
        String result = geminiApiClient.callGeminiApi("test");

        // Assert
		assertThat(result).isNotNull();
		verify(mockModels, times(3)).generateContent(anyString(), anyList(), any());
	}

    /**
     * Test #6: Verify Retry ignores specific exceptions.
     */
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
            // Expected
        }

		// Verify : Called only once
		verify(mockModels, times(1)).generateContent(anyString(), anyList(), any());
	}

    /**
     * Test #7: Verify the full async flow (Integration of components).
     * Since we mock UpdaterService, we verify interactions instead of DB state.
     */
    @Test
    @DisplayName("Full flow: Service orchestrates calls correctly")
    void fullFlow_successfulGeneration() throws Exception {
        // Arrange
        mockSuccessfulApiCall();
        Long jobId = 123L;
        
		// Act
		// Note: This executes synchronously here because we haven't defined a real
		// TaskExecutor in this test context
		geminiApiClient.generateAndStageHistory(jobId, "test prompt");

        // Wait for async completion
        Thread.sleep(2000);

		// Assert
		// 1. Status updated to DOWNLOADING
		verify(jobUpdaterService).updateJobStatus(Mockito.eq(jobId), Mockito.eq(JobStatus.DOWNLOADING));
		// 2. Data staged (Success path)
		verify(jobUpdaterService).stageJobResponse(Mockito.eq(jobId), anyString());
		// 3. No failure methods called
		verify(jobUpdaterService, never()).markJobAsFailed(Mockito.anyLong(), any());
	}

    /**
     * Test #8: Load Test with 100 requests.
     * Verifies system behavior under load exceeding Rate Limits.
     */
    @Test
    @DisplayName("Load test: Handles 100 concurrent requests")
    void loadTest_handles100ConcurrentRequests() throws Exception {
        // Arrange
        mockSuccessfulApiCall();
        
        int totalRequests = 100;
        CountDownLatch latch = new CountDownLatch(totalRequests);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger rejectedCount = new AtomicInteger(0);
      
        ExecutorService executor = Executors.newFixedThreadPool(20);

		for (int i = 0; i < totalRequests; i++) {
			executor.submit(() -> {
				try {
					geminiApiClient.callGeminiApi("test");
					successCount.incrementAndGet();
				} catch (Exception e) {
					if (e.getMessage().contains("unavailable")) {
						rejectedCount.incrementAndGet();
					}
				} finally {
					latch.countDown();
				}
			});
		}

		latch.await(10, TimeUnit.SECONDS);
		executor.shutdown();

		System.out.println("Success: " + successCount.get() + ", Rejected: " + rejectedCount.get());
		assertThat(successCount.get()).as("Only ~3 calls should succeed").isLessThan(10);
		assertThat(rejectedCount.get()).as("Most calls should be rejected").isGreaterThan(90);
	}

    /**
     * Test #9: Edge case - Fallback handling.
     */
    @Test
    @DisplayName("Edge case: Empty response triggers fallback exception")
    void edgeCase_emptyApiResponse() {
        // Arrange
        GenerateContentResponse emptyResponse = mock(GenerateContentResponse.class);
        when(emptyResponse.text()).thenReturn("");
        when(emptyResponse.candidates()).thenReturn(Optional.empty());
        
        when(mockModels.generateContent(anyString(), anyList(), any()))
            .thenReturn(emptyResponse);

        // Act & Assert
        try {
            geminiApiClient.callGeminiApi("test");
        } catch (Exception e) {
            assertThat(e.getMessage()).contains("temporarily unavailable");
        }
    }

    /**
     * Test #10: Exponential Backoff Verification.
     * Config: initial=50ms, multiplier=2.
     * Expectation:
     * 1. Fail -> Wait 50ms
     * 2. Fail -> Wait 100ms
     * 3. Success
     * Total wait time approx 150ms + execution time.
     */
    @Test
    @DisplayName("Retry використовує exponential backoff (100ms, 200ms, 400ms)")
    void retry_usesExponentialBackoff() {
        // Arrange
        when(mockModels.generateContent(anyString(), anyList(), any()))
            .thenThrow(new RuntimeException("Timeout 1"))
            .thenThrow(new RuntimeException("Timeout 2"))
            .thenReturn(mockSuccessResponse());

        // Act
        long startTime = System.currentTimeMillis();
        geminiApiClient.callGeminiApi("test");
        long duration = System.currentTimeMillis() - startTime;

		// Assert
		// 50ms + 100ms = 150ms minimum wait.
		// We check > 140ms to account for slight timer inaccuracies, but definitely > 50ms (fixed)
        assertThat(duration).isGreaterThan(140);
        verify(mockModels, times(3)).generateContent(anyString(), anyList(), any());
    }

    private void mockSuccessfulApiCall() {
		GenerateContentResponse response = mock(GenerateContentResponse.class);
		when(response.text()).thenReturn("{\"dailyMetrics\": [], \"activities\": []}");

		when(mockModels.generateContent(anyString(), anyList(), any())).thenReturn(response);
    }

    private GenerateContentResponse mockSuccessResponse() {
        GenerateContentResponse response = mock(GenerateContentResponse.class);
        when(response.text()).thenReturn(
            "{\"dailyMetrics\": [], \"activities\": []}"
        );
        return response;
    }
}
