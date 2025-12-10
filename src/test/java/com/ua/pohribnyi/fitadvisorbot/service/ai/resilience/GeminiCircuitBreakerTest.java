package com.ua.pohribnyi.fitadvisorbot.service.ai.resilience;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration;
import io.github.resilience4j.springboot3.ratelimiter.autoconfigure.RateLimiterAutoConfiguration;
import io.github.resilience4j.springboot3.retry.autoconfigure.RetryAutoConfiguration;

/**
 * Unit tests for CircuitBreaker pattern in GeminiApiClient.
 * 
 * Testing:
 * - State transitions (CLOSED → OPEN → HALF_OPEN)
 * - Failure threshold triggering
 * - Request blocking in OPEN state
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
    RetryAutoConfiguration.class
})
@TestPropertySource(properties = {
    // CircuitBreaker: Primary focus
    "resilience4j.circuitbreaker.instances.geminiApi.sliding-window-size=3",
    "resilience4j.circuitbreaker.instances.geminiApi.minimum-number-of-calls=3",
    "resilience4j.circuitbreaker.instances.geminiApi.failure-rate-threshold=50",
    "resilience4j.circuitbreaker.instances.geminiApi.wait-duration-in-open-state=5s",
    "resilience4j.circuitbreaker.instances.geminiApi.permitted-number-of-calls-in-half-open-state=1",
    
    // RateLimiter: Disabled (high limit)
    "resilience4j.ratelimiter.instances.geminiApi.limit-for-period=1000",
    "resilience4j.ratelimiter.instances.geminiApi.limit-refresh-period=1s",
    
    // Retry: Disabled
    "resilience4j.retry.instances.geminiApi.max-attempts=1",
    
    "google.gemini.api.key=fake-key"
})
class GeminiCircuitBreakerTest {

    @Autowired
    private GeminiApiClient geminiApiClient;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

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

    @BeforeEach
    void setUp() {
        mockModels = mock(Models.class);
        ReflectionTestUtils.setField(mockGeminiClient, "models", mockModels);

        // Reset CircuitBreaker state
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("geminiApi");
        cb.transitionToClosedState();
        cb.reset();
    }

    @Test
    @DisplayName("CircuitBreaker opens after 50% failures (2 out of 3)")
    void circuitBreaker_opensAfterFailureThreshold() {
        // Arrange: API fails
        when(mockModels.generateContent(anyString(), anyList(), any()))
            .thenThrow(new RuntimeException("API Error"));

        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("geminiApi");
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

        // Act: Call 3 times (minimum-number-of-calls)
        for (int i = 0; i < 3; i++) {
            try {
                geminiApiClient.callGeminiApi("test");
            } catch (Exception e) {
                // Expected
            }
        }

        // Assert: CircuitBreaker opened
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(cb.getMetrics().getNumberOfFailedCalls()).isEqualTo(3);
    }

    @Test
    @DisplayName("CircuitBreaker blocks requests when OPEN")
    void circuitBreaker_blocksRequestsInOpenState() {
        // Arrange: Force OPEN state
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("geminiApi");
        cb.transitionToOpenState();

        // Act & Assert: Request is rejected immediately
        assertThatThrownBy(() -> geminiApiClient.callGeminiApi("test"))
            .hasMessageContaining("unavailable");

        // Verify: API never called
        verify(mockModels, never()).generateContent(anyString(), anyList(), any());
    }

    @Test
    @DisplayName("CircuitBreaker transitions to HALF_OPEN after wait duration")
    void circuitBreaker_transitionsToHalfOpen() throws Exception {
        // Arrange: Open circuit breaker
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("geminiApi");
        cb.transitionToOpenState();
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // Act: Wait for wait-duration (5 seconds)
        Thread.sleep(6_000);

        // Force state transition by attempting a call
        mockSuccessfulApiCall();
        try {
            geminiApiClient.callGeminiApi("test");
        } catch (Exception e) {
            // May fail if not yet transitioned
        }

        // Assert: State is HALF_OPEN
        assertThat(cb.getState()).isIn(
            CircuitBreaker.State.HALF_OPEN,
            CircuitBreaker.State.CLOSED // May transition directly on success
        );
    }

    @Test
    @DisplayName("CircuitBreaker closes after successful call in HALF_OPEN")
    void circuitBreaker_closesAfterSuccessInHalfOpen() throws Exception {
        // Arrange: Force HALF_OPEN state
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("geminiApi");
        cb.transitionToOpenState();
        Thread.sleep(6_000);
        cb.transitionToHalfOpenState();

        mockSuccessfulApiCall();

        // Act: Successful call
        String result = geminiApiClient.callGeminiApi("test");

        // Assert: CircuitBreaker closed
        assertThat(result).isNotNull();
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    @DisplayName("CircuitBreaker reopens after failure in HALF_OPEN")
    void circuitBreaker_reopensAfterFailureInHalfOpen() throws Exception {
        // Arrange: Force HALF_OPEN state
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("geminiApi");
        cb.transitionToOpenState();
        Thread.sleep(6_000);
        cb.transitionToHalfOpenState();

        when(mockModels.generateContent(anyString(), anyList(), any()))
            .thenThrow(new RuntimeException("Still failing"));

        // Act: Failed call
        try {
            geminiApiClient.callGeminiApi("test");
        } catch (Exception e) {
            // Expected
        }

        // Assert: CircuitBreaker reopened
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    @DisplayName("CircuitBreaker metrics track failures accurately")
    void circuitBreaker_metricsAreAccurate() {
        // Arrange
        when(mockModels.generateContent(anyString(), anyList(), any()))
            .thenThrow(new RuntimeException("Fail 1"))
            .thenThrow(new RuntimeException("Fail 2"))
            .thenReturn(mockSuccessResponse());

        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("geminiApi");

        // Act: 2 failures, 1 success
        try { geminiApiClient.callGeminiApi("test"); } catch (Exception e) {}
        try { geminiApiClient.callGeminiApi("test"); } catch (Exception e) {}
        geminiApiClient.callGeminiApi("test");

        // Assert
        CircuitBreaker.Metrics metrics = cb.getMetrics();
        assertThat(metrics.getNumberOfFailedCalls()).isEqualTo(2);
        assertThat(metrics.getNumberOfSuccessfulCalls()).isEqualTo(1);
        assertThat(metrics.getFailureRate()).isEqualTo(66.66666f, org.assertj.core.api.Assertions.within(0.01f));
    }

    private void mockSuccessfulApiCall() {
        GenerateContentResponse response = mock(GenerateContentResponse.class);
        when(response.text()).thenReturn("{\"dailyMetrics\": [], \"activities\": []}");
        when(mockModels.generateContent(anyString(), anyList(), any())).thenReturn(response);
    }

    private GenerateContentResponse mockSuccessResponse() {
        GenerateContentResponse response = mock(GenerateContentResponse.class);
        when(response.text()).thenReturn("{\"dailyMetrics\": [], \"activities\": []}");
        return response;
    }
}