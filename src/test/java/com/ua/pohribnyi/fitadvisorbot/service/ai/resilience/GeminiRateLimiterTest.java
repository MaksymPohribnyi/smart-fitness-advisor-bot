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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
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

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration;
import io.github.resilience4j.springboot3.ratelimiter.autoconfigure.RateLimiterAutoConfiguration;
import io.github.resilience4j.springboot3.retry.autoconfigure.RetryAutoConfiguration;

/**
 * Unit tests for RateLimiter pattern in GeminiApiClient.
 * 
 * Testing: - Request throttling (3 req/10s) - Rejection after limit exceeded -
 * High concurrency behavior
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { GeminiApiClient.class, GeminiConfigFactory.class, GeminiSchemaDefiner.class })
@EnableConfigurationProperties
@ImportAutoConfiguration(classes = { AopAutoConfiguration.class, RateLimiterAutoConfiguration.class,
		CircuitBreakerAutoConfiguration.class, RetryAutoConfiguration.class })
@TestPropertySource(properties = {
		// RateLimiter: 3 requests per 10 seconds
		"resilience4j.ratelimiter.instances.geminiApi.limit-for-period=3",
		"resilience4j.ratelimiter.instances.geminiApi.limit-refresh-period=10s",
		"resilience4j.ratelimiter.instances.geminiApi.timeout-duration=100ms",

		// CircuitBreaker: Disabled
		"resilience4j.circuitbreaker.instances.geminiApi.minimum-number-of-calls=1000",
		"resilience4j.circuitbreaker.instances.geminiApi.failure-rate-threshold=99",

		// Retry: Disabled
		"resilience4j.retry.instances.geminiApi.max-attempts=1",

		"google.gemini.api.key=fake-key" })
class GeminiRateLimiterTest {

	@Autowired
	private GeminiApiClient geminiApiClient;

	@Autowired
	private RateLimiterRegistry rateLimiterRegistry;

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

		rateLimiterRegistry.remove("geminiApi");

		RateLimiterConfig config = RateLimiterConfig.custom()
				.limitForPeriod(3)
				.limitRefreshPeriod(Duration.ofSeconds(10))
				.timeoutDuration(Duration.ofMillis(100))
				.build();

		rateLimiterRegistry.rateLimiter("geminiApi", config);
	}

	@Test
	@DisplayName("RateLimiter allows first 3 requests within period")
	void rateLimiter_allowsThreeRequests() throws Exception {
		// Arrange
		mockSuccessfulApiCall();
		CountDownLatch latch = new CountDownLatch(3);
		AtomicInteger successCount = new AtomicInteger(0);

		// Act: Fire 3 concurrent requests
		for (int i = 0; i < 3; i++) {
			new Thread(() -> {
				try {
					geminiApiClient.callGeminiApi("test");
					successCount.incrementAndGet();
				} catch (Exception e) {
					System.err.println("Request failed: " + e.getMessage());
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

	@Test
	@DisplayName("RateLimiter blocks 4th request immediately")
	void rateLimiter_blocksFourthRequest() throws Exception {
		// Arrange
		mockSuccessfulApiCall();
		AtomicInteger successCount = new AtomicInteger(0);
		AtomicInteger blockedCount = new AtomicInteger(0);
		CountDownLatch latch = new CountDownLatch(4);

		// Act: Fire 4 requests sequentially to avoid race condition
		ExecutorService executor = Executors.newSingleThreadExecutor();
		for (int i = 0; i < 4; i++) {
			final int requestNum = i + 1;
			executor.submit(() -> {
				try {
					String result = geminiApiClient.callGeminiApi("test-" + requestNum);
					successCount.incrementAndGet();
					System.out.println("✅ Request " + requestNum + " succeeded");
				} catch (Exception e) {
					String msg = e.getMessage().toLowerCase();
					if (msg.contains("rate") || msg.contains("permit") || msg.contains("unavailable")) {
						blockedCount.incrementAndGet();
						System.out.println("❌ Request " + requestNum + " blocked: " + msg);
					} else {
						System.err.println("⚠️ Request " + requestNum + " failed unexpectedly: " + msg);
					}
				} finally {
					latch.countDown();
				}
			});
		}

		latch.await(5, TimeUnit.SECONDS);
		executor.shutdown();

		// Assert
		System.out.println("Success: " + successCount.get() + ", Blocked: " + blockedCount.get());
		assertThat(successCount.get()).as("Should allow exactly 3 requests").isEqualTo(3);
		assertThat(blockedCount.get()).as("Should block exactly 1 request").isEqualTo(1);
	}

	@Test
	@DisplayName("RateLimiter resets after refresh period")
	void rateLimiter_resetsAfterPeriod() throws Exception {
		// Arrange
		mockSuccessfulApiCall();

		// Act: Use all 3 permits
		for (int i = 0; i < 3; i++) {
			geminiApiClient.callGeminiApi("test-" + i);
		}

		// Verify: 4th request blocked
		boolean wasBlocked = false;
		try {
			geminiApiClient.callGeminiApi("test-should-block");
		} catch (Exception e) {
			if (e.getMessage().toLowerCase().contains("unavailable")) {
				wasBlocked = true;
			}
		}
		assertThat(wasBlocked).as("4th request should be blocked").isTrue();

		// Wait for refresh period (10 seconds) + buffer
		System.out.println("⏳ Waiting 11 seconds for RateLimiter refresh...");
		Thread.sleep(11_000);

		// Assert: New request succeeds after reset
		String result = geminiApiClient.callGeminiApi("test-after-reset");
		assertThat(result).isNotNull();

		// Total calls: 3 initial + 1 blocked attempt + 1 after reset = 5 successful API
		// calls
		verify(mockModels, times(4)).generateContent(anyString(), anyList(), any());
	}

	@Test
	@DisplayName("High concurrency: 100 requests respect rate limit")
	void rateLimiter_handlesHighConcurrency() throws Exception {
		// Arrange
		mockSuccessfulApiCall();
		int totalRequests = 100;
		CountDownLatch latch = new CountDownLatch(totalRequests);
		AtomicInteger successCount = new AtomicInteger(0);
		AtomicInteger rejectedCount = new AtomicInteger(0);

		ExecutorService executor = Executors.newFixedThreadPool(20);

		// Act: Fire 100 requests as fast as possible
		for (int i = 0; i < totalRequests; i++) {
			executor.submit(() -> {
				try {
					geminiApiClient.callGeminiApi("test");
					successCount.incrementAndGet();
				} catch (Exception e) {
					rejectedCount.incrementAndGet();
				} finally {
					latch.countDown();
				}
			});
		}

		boolean completed = latch.await(5, TimeUnit.SECONDS);
		executor.shutdown();

		// Assert: Only 3 requests succeeded (within 100ms timeout window)
		System.out.println("✅ Success: " + successCount.get() + ", ❌ Rejected: " + rejectedCount.get());
		assertThat(completed).as("All requests should complete").isTrue();
		assertThat(successCount.get()).as("Only 3 calls within period").isEqualTo(3);
		assertThat(rejectedCount.get()).as("97 calls rejected").isEqualTo(97);
	}

	@Test
	@DisplayName("RateLimiter metrics are accurate")
	void rateLimiter_metricsAreCorrect() {
		// Arrange
		mockSuccessfulApiCall();
		RateLimiter rateLimiter = rateLimiterRegistry.rateLimiter("geminiApi");

		// Act: Use 2 permits
		geminiApiClient.callGeminiApi("test-1");
		geminiApiClient.callGeminiApi("test-2");

		// Assert: Metrics reflect usage
		RateLimiter.Metrics metrics = rateLimiter.getMetrics();
		System.out.println("Available permits: " + metrics.getAvailablePermissions());
		System.out.println("Waiting threads: " + metrics.getNumberOfWaitingThreads());

		assertThat(metrics.getAvailablePermissions()).isEqualTo(1); // 3 - 2 = 1
		assertThat(metrics.getNumberOfWaitingThreads()).isEqualTo(0);
	}

	private void mockSuccessfulApiCall() {
		GenerateContentResponse response = mock(GenerateContentResponse.class);
		when(response.text()).thenReturn("{\"dailyMetrics\": [], \"activities\": []}");
		when(mockModels.generateContent(anyString(), anyList(), any())).thenReturn(response);
	}
}
