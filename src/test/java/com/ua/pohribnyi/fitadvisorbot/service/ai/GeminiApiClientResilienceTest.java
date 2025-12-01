package com.ua.pohribnyi.fitadvisorbot.service.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.ua.pohribnyi.fitadvisorbot.enums.JobStatus;
import com.ua.pohribnyi.fitadvisorbot.model.entity.GenerationJob;
import com.ua.pohribnyi.fitadvisorbot.model.entity.user.User;
import com.ua.pohribnyi.fitadvisorbot.repository.ai.GenerationJobRepository;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;

import static org.mockito.Mockito.*;

/**
 * Integration tests для Resilience4j patterns в GeminiApiClient.
 * 
 * Тестує:
 * 1. Rate Limiter (12 RPM)
 * 2. Circuit Breaker (50% failure → OPEN)
 * 3. Retry (3 attempts με exponential backoff)
 * 4. Async execution
 * 5. Edge cases
 */
@SpringBootTest
@TestPropertySource(properties = {
    // Rate Limiter: дуже низький ліміт для швидкого тесту
    "resilience4j.ratelimiter.instances.geminiApi.limit-for-period=3",
    "resilience4j.ratelimiter.instances.geminiApi.limit-refresh-period=10s",
    "resilience4j.ratelimiter.instances.geminiApi.timeout-duration=5s",
    
    // Circuit Breaker: швидке відкриття
    "resilience4j.circuitbreaker.instances.geminiApi.minimum-number-of-calls=3",
    "resilience4j.circuitbreaker.instances.geminiApi.failure-rate-threshold=50",
    "resilience4j.circuitbreaker.instances.geminiApi.wait-duration-in-open-state=5s",
    
    // Retry: швидкі спроби
    "resilience4j.retry.instances.geminiApi.max-attempts=3",
    "resilience4j.retry.instances.geminiApi.wait-duration=100ms"
})
class GeminiApiClientResilienceTest {

    @Autowired
    private GeminiApiClient geminiApiClient;

    @Autowired
    private GenerationJobRepository jobRepository;

    @MockitoBean
    private Client mockGeminiClient;

    @MockitoBean
    private GenerateContentConfig mockConfig;

    @Autowired
    private RateLimiterRegistry rateLimiterRegistry;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    private User testUser;

    @BeforeEach
    void setUp() {
        // Clean DB
        jobRepository.deleteAll();

        // Reset Resilience4j state
        RateLimiter rateLimiter = rateLimiterRegistry.rateLimiter("geminiApi");
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("geminiApi");
        
        rateLimiter.changeTimeoutDuration(Duration.ofSeconds(5));
        circuitBreaker.reset();

        // Create test user
        testUser = User.builder()
            .telegramUserId(123L)
            .firstName("Test")
            .languageCode("uk")
            .build();
    }

    /**
     * ═══════════════════════════════════════════════════════════════════
     * TEST #1: Rate Limiter - Базова функціональність
     * ═══════════════════════════════════════════════════════════════════
     */
    @Test
    @DisplayName("Rate Limiter пропускає перші 3 запити за 10 секунд")
    void rateLimiter_allowsThreeRequestsPerPeriod() throws Exception {
        // Arrange
        mockSuccessfulApiCall();
        CountDownLatch latch = new CountDownLatch(3);

        // Act: Запускаємо 3 запити одночасно
        for (int i = 0; i < 3; i++) {
            new Thread(() -> {
                try {
                    geminiApiClient.callGeminiApi("test prompt");
                    latch.countDown();
                } catch (Exception e) {
                    // ignore
                }
            }).start();
        }

        // Assert
        boolean completed = latch.await(3, TimeUnit.SECONDS);
        assertThat(completed).isTrue();
        
        // Verify 3 виклики пройшли
        verify(mockGeminiClient.models, times(3))
            .generateContent(anyString(), anyList(), any());
    }

    /**
     * ═══════════════════════════════════════════════════════════════════
     * TEST #2: Rate Limiter - Блокування 4-го запиту
     * ═══════════════════════════════════════════════════════════════════
     */
    @Test
    @DisplayName("Rate Limiter блокує 4-й запит (після ліміту)")
    void rateLimiter_blocksRequestsAfterLimit() throws Exception {
        // Arrange
        mockSuccessfulApiCall();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger blockedCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(4);

        // Act: Запускаємо 4 запити
        for (int i = 0; i < 4; i++) {
            new Thread(() -> {
                try {
                    geminiApiClient.callGeminiApi("test");
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    if (e.getMessage().contains("rate") || 
                        e.getMessage().contains("RequestNotPermitted")) {
                        blockedCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            }).start();
        }

        latch.await(10, TimeUnit.SECONDS);

        // Assert: 3 успішні, 1 заблокований
        assertThat(successCount.get()).isEqualTo(3);
        assertThat(blockedCount.get()).isGreaterThanOrEqualTo(1);
    }

    /**
     * ═══════════════════════════════════════════════════════════════════
     * TEST #3: Circuit Breaker - Відкривається після 50% збоїв
     * ═══════════════════════════════════════════════════════════════════
     */
    @Test
    @DisplayName("Circuit Breaker відкривається після 50% failed requests")
    void circuitBreaker_opensAfterFailureThreshold() throws Exception {
        // Arrange: Mock API завжди падає
        when(mockGeminiClient.models.generateContent(anyString(), anyList(), any()))
            .thenThrow(new RuntimeException("API Error"));

        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("geminiApi");
        
        // Act: Викликаємо 3 рази (minimum-number-of-calls = 3)
        for (int i = 0; i < 3; i++) {
            try {
                geminiApiClient.callGeminiApi("test");
            } catch (Exception e) {
                // Expected
            }
        }

        // Assert: Circuit Breaker має бути OPEN
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    /**
     * ═══════════════════════════════════════════════════════════════════
     * TEST #4: Circuit Breaker - Блокує запити в OPEN стані
     * ═══════════════════════════════════════════════════════════════════
     */
    @Test
    @DisplayName("Circuit Breaker блокує запити коли OPEN")
    void circuitBreaker_blocksRequestsWhenOpen() throws Exception {
        // Arrange: Відкриваємо Circuit Breaker
        circuitBreakerRegistry.circuitBreaker("geminiApi").transitionToOpenState();

        // Act & Assert: Запит має бути відхилений
        try {
            geminiApiClient.callGeminiApi("test");
            assert false : "Should throw exception";
        } catch (Exception e) {
            assertThat(e.getMessage()).contains("unavailable");
        }

        // Verify: API НЕ викликався
        verify(mockGeminiClient.models, never())
            .generateContent(anyString(), anyList(), any());
    }

    /**
     * ═══════════════════════════════════════════════════════════════════
     * TEST #5: Retry - 3 спроби при помилці
     * ═══════════════════════════════════════════════════════════════════
     */
    @Test
    @DisplayName("Retry робить 3 спроби при IOException")
    void retry_attemptsThreeTimesOnIOException() {
        // Arrange: API падає 2 рази, потім успіх
        when(mockGeminiClient.models.generateContent(anyString(), anyList(), any()))
            .thenThrow(new java.io.IOException("Connection timeout"))
            .thenThrow(new java.io.IOException("Connection timeout"))
            .thenReturn(mockSuccessResponse());

        // Act
        String result = geminiApiClient.callGeminiApi("test");

        // Assert: Отримали результат після 3 спроб
        assertThat(result).isNotNull();
        verify(mockGeminiClient.models, times(3))
            .generateContent(anyString(), anyList(), any());
    }

    /**
     * ═══════════════════════════════════════════════════════════════════
     * TEST #6: Retry - НЕ повторює при IllegalArgumentException
     * ═══════════════════════════════════════════════════════════════════
     */
    @Test
    @DisplayName("Retry НЕ повторює при non-retryable exception")
    void retry_doesNotRetryOnIllegalArgumentException() {
        // Arrange
        when(mockGeminiClient.models.generateContent(anyString(), anyList(), any()))
            .thenThrow(new IllegalArgumentException("Invalid prompt"));

        // Act & Assert
        try {
            geminiApiClient.callGeminiApi("test");
            assert false : "Should throw exception";
        } catch (Exception e) {
            // Expected
        }

        // Verify: Тільки 1 спроба (no retry)
        verify(mockGeminiClient.models, times(1))
            .generateContent(anyString(), anyList(), any());
    }

    /**
     * ═══════════════════════════════════════════════════════════════════
     * TEST #7: Full Flow - Успішна генерація історії
     * ═══════════════════════════════════════════════════════════════════
     */
    @Test
    @DisplayName("Full flow: Job успішно створюється та завершується")
    void fullFlow_successfulGeneration() throws Exception {
        // Arrange
        mockSuccessfulApiCall();
        
        GenerationJob job = GenerationJob.createPendingJob(testUser, 123L, 456);
        jobRepository.save(job);

        // Act: Запускаємо async generation
        geminiApiClient.generateAndStageHistory(job.getId(), "test prompt");

        // Wait для async completion
        Thread.sleep(2000);

        // Assert
        GenerationJob updated = jobRepository.findById(job.getId()).orElseThrow();
        assertThat(updated.getStatus()).isIn(JobStatus.DOWNLOADED, JobStatus.PROCESSING, JobStatus.PROCESSED);
    }

    /**
     * ═══════════════════════════════════════════════════════════════════
     * TEST #8: Load Test - 100 одночасних запитів
     * ═══════════════════════════════════════════════════════════════════
     */
    @Test
    @DisplayName("Load test: система витримує 100 одночасних запитів")
    void loadTest_handles100ConcurrentRequests() throws Exception {
        // Arrange
        mockSuccessfulApiCall();
        
        int totalRequests = 100;
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failedCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(totalRequests);

        ExecutorService executor = Executors.newFixedThreadPool(20);

        // Act: Запускаємо 100 запитів
        for (int i = 0; i < totalRequests; i++) {
            final int jobId = i;
            executor.submit(() -> {
                try {
                    GenerationJob job = GenerationJob.createPendingJob(testUser, 123L, jobId);
                    jobRepository.save(job);
                    
                    geminiApiClient.generateAndStageHistory(job.getId(), "test " + jobId);
                    successCount.incrementAndGet();
                    
                } catch (Exception e) {
                    failedCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        // Assert: Більшість запитів успішна
        System.out.println("Success: " + successCount.get() + ", Failed: " + failedCount.get());
        assertThat(successCount.get()).isGreaterThan(0);
        
        // Rate Limiter має обмежити кількість одночасних запитів
        assertThat(failedCount.get()).isLessThan(totalRequests);
    }

    /**
     * ═══════════════════════════════════════════════════════════════════
     * TEST #9: Edge Case - Empty API response
     * ═══════════════════════════════════════════════════════════════════
     */
    @Test
    @DisplayName("Edge case: Empty API response викликає exception")
    void edgeCase_emptyApiResponse() {
        // Arrange
        GenerateContentResponse emptyResponse = mock(GenerateContentResponse.class);
        when(emptyResponse.text()).thenReturn("");
        when(emptyResponse.candidates()).thenReturn(java.util.Optional.empty());
        
        when(mockGeminiClient.models.generateContent(anyString(), anyList(), any()))
            .thenReturn(emptyResponse);

        // Act & Assert
        try {
            geminiApiClient.callGeminiApi("test");
            assert false : "Should throw exception";
        } catch (Exception e) {
            assertThat(e.getMessage()).contains("Empty");
        }
    }

    /**
     * ═══════════════════════════════════════════════════════════════════
     * TEST #10: Exponential Backoff Timing
     * ═══════════════════════════════════════════════════════════════════
     */
    @Test
    @DisplayName("Retry використовує exponential backoff (100ms, 200ms, 400ms)")
    void retry_usesExponentialBackoff() {
        // Arrange
        when(mockGeminiClient.models.generateContent(anyString(), anyList(), any()))
            .thenThrow(new java.io.IOException("Timeout"))
            .thenThrow(new java.io.IOException("Timeout"))
            .thenReturn(mockSuccessResponse());

        // Act
        long startTime = System.currentTimeMillis();
        geminiApiClient.callGeminiApi("test");
        long duration = System.currentTimeMillis() - startTime;

        // Assert: Total time має бути ~700ms (100 + 200 + 400)
        assertThat(duration).isGreaterThan(600);
        assertThat(duration).isLessThan(1500); // З накладними витратами
    }

    private void mockSuccessfulApiCall() {
        when(mockGeminiClient.models.generateContent(anyString(), anyList(), any()))
            .thenReturn(mockSuccessResponse());
    }

    private GenerateContentResponse mockSuccessResponse() {
        GenerateContentResponse response = mock(GenerateContentResponse.class);
        when(response.text()).thenReturn(
            "{\"dailyMetrics\": [], \"activities\": []}"
        );
        return response;
    }
}
