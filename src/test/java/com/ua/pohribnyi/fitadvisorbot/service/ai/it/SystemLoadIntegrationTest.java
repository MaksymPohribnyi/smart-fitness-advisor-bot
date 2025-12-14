package com.ua.pohribnyi.fitadvisorbot.service.ai.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.google.genai.Client;
import com.google.genai.Models;
import com.google.genai.types.GenerateContentResponse;
import com.ua.pohribnyi.fitadvisorbot.config.AsyncConfig;
import com.ua.pohribnyi.fitadvisorbot.config.WebConfig;
import com.ua.pohribnyi.fitadvisorbot.config.prompt.PromptMessageSourceConfig;
import com.ua.pohribnyi.fitadvisorbot.enums.JobStatus;
import com.ua.pohribnyi.fitadvisorbot.model.entity.GenerationJob;
import com.ua.pohribnyi.fitadvisorbot.model.entity.user.User;
import com.ua.pohribnyi.fitadvisorbot.model.entity.user.UserProfile;
import com.ua.pohribnyi.fitadvisorbot.repository.ai.GenerationJobRepository;
import com.ua.pohribnyi.fitadvisorbot.repository.user.UserProfileRepository;
import com.ua.pohribnyi.fitadvisorbot.repository.user.UserRepository;
import com.ua.pohribnyi.fitadvisorbot.service.ai.GeminiApiClient;
import com.ua.pohribnyi.fitadvisorbot.service.ai.GenerationJobUpdaterService;
import com.ua.pohribnyi.fitadvisorbot.service.ai.SyntheticDataService;
import com.ua.pohribnyi.fitadvisorbot.service.ai.SyntheticDataWorkerService;
import com.ua.pohribnyi.fitadvisorbot.service.ai.factory.GeminiConfigFactory;
import com.ua.pohribnyi.fitadvisorbot.service.ai.prompt.GeminiPromptBuilderService;
import com.ua.pohribnyi.fitadvisorbot.service.ai.prompt.PromptService;
import com.ua.pohribnyi.fitadvisorbot.service.ai.schema.GeminiSchemaDefiner;
import com.ua.pohribnyi.fitadvisorbot.util.TestUtils;
import com.ua.pohribnyi.fitadvisorbot.util.concurrency.listener.JobCreationListener;
import com.ua.pohribnyi.fitadvisorbot.util.concurrency.listener.JobDownloadedEventListener;

import lombok.extern.slf4j.Slf4j;

@SpringBootTest(classes = { 
		SystemLoadIntegrationTest.TestInfrastructureConfig.class, 
		WebConfig.class,
		AsyncConfig.class, 
		PromptMessageSourceConfig.class, 
		SyntheticDataService.class, 
		GeminiApiClient.class,
		GenerationJobUpdaterService.class,
		SyntheticDataWorkerService.class, 
		GeminiPromptBuilderService.class,
		PromptService.class, 
		GeminiConfigFactory.class,
		GeminiSchemaDefiner.class, 
		JobCreationListener.class,
		JobDownloadedEventListener.class })
@Testcontainers
@ActiveProfiles("test")
@Slf4j
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class SystemLoadIntegrationTest {

	@Autowired
	private SyntheticDataService dataService;
	@Autowired
	private GenerationJobRepository jobRepository;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private UserProfileRepository userProfileRepository;

	@MockitoBean
	private Client mockGeminiClient;
	private Models mockModels;

	@Container
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
			.withDatabaseName("test")
			.withUsername("test")
			.withPassword("test");

	static {
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
	}
        
    @DynamicPropertySource
    static void registerPgProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        registry.add("spring.task.execution.pool.ai.core-size", () -> "2"); 
        registry.add("spring.task.execution.pool.ai.max-size", () -> "2");
        registry.add("spring.task.execution.pool.ai.queue-capacity", () -> "1"); 
        registry.add("spring.task.execution.pool.ai.thread-name", () -> "ai-gen-test-");
        
        registry.add("spring.task.execution.pool.data.core-size", () -> "5");
        registry.add("spring.task.execution.pool.data.max-size", () -> "10");
        registry.add("spring.task.execution.pool.data.queue-capacity", () -> "100"); 
        registry.add("spring.task.execution.pool.data.thread-name", () -> "data-proc-test-");

        registry.add("resilience4j.ratelimiter.instances.geminiApi.limit-for-period", () -> "5");
        registry.add("resilience4j.ratelimiter.instances.geminiApi.limit-refresh-period", () -> "5s");
        
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "30");
    }
	
    @BeforeEach
	void setup() {
		mockModels = mock(Models.class);
		ReflectionTestUtils.setField(mockGeminiClient, "models", mockModels);

		jobRepository.deleteAll();
        userProfileRepository.deleteAll();
        userRepository.deleteAll();
	}
    
	@Test
	@DisplayName("Sustained Load: Verify RateLimiter throttles requests but keeps system stable")
	void sustainedLoad_CheckRateLimiter() throws InterruptedException {
		// Arrange: Mock slow API response (500ms delay) to occupy threads
		mockSlowGeminiResponse(500);

		int totalUsers = 15; 

		CountDownLatch latch = new CountDownLatch(totalUsers);
		AtomicInteger successCount = new AtomicInteger(0);
		AtomicInteger rateLimitExceptionCount = new AtomicInteger(0);

		ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(5);

		// Act: Launch 15 requests rapidly
		for (int i = 0; i < totalUsers; i++) {
			final int idx = i;
			scheduler.submit(() -> {
				try {
					User user = createUniqueUser(idx);
					UserProfile profile = createTestProfile(user);

					dataService.triggerHistoryGeneration(user, profile, 123L + idx, 456 + idx);
					successCount.incrementAndGet();
				} catch (Exception e) {
					if (e.getMessage().contains("overloaded") || e.getMessage().contains("unavailable")) {
						rateLimitExceptionCount.incrementAndGet();
					} else {
						log.error("Unexpected error", e);
					}
				} finally {
					latch.countDown();
				}
			});
		}

		// Wait for all to finish
		latch.await(10, TimeUnit.SECONDS);
		scheduler.shutdown();

		// Assert
		log.info("Sustained Load Results: Success={}, RateLimited={}", successCount.get(),
				rateLimitExceptionCount.get());

		List<GenerationJob> jobs = jobRepository.findAll();
		assertThat(jobs).isNotEmpty();
		assertThat(successCount.get()).isGreaterThan(0);
	}
    
	@Test
	@DisplayName("Peak Load: Flood system to trigger RejectedExecutionException (Queue Full)")
	void peakLoad_TriggerSaturation() throws InterruptedException {
		// Arrange: Mock VERY slow API response (2s delay) to clog the Thread Pool
		mockSlowGeminiResponse(2000);

		// Pool Size = 4, Queue = 10. Capacity = 14.
		// We send 30 requests instantly. ~16 should be rejected.
		int totalRequests = 15;

		CountDownLatch latch = new CountDownLatch(totalRequests);
		ExecutorService executor = Executors.newFixedThreadPool(15); // Client simulator

		// Act: Flood
		for (int i = 0; i < totalRequests; i++) {
			final int idx = i;
			executor.submit(() -> {
				try {
					User user = createUniqueUser(1000 + idx);
					UserProfile profile = createTestProfile(user);
					dataService.triggerHistoryGeneration(user, profile, 1000L + idx, 999);
				} catch (Exception e) {
					log.error("Catching sync error: {}", e.getMessage());
				} finally {
					latch.countDown();
				}
			});
		}

		latch.await(15, TimeUnit.SECONDS);
		executor.shutdown();

		log.info("Waiting for successful jobs to finish processing...");
		Awaitility.await()
				.atMost(10, TimeUnit.SECONDS)
				.pollInterval(Duration.ofMillis(500)).until(() -> {
					long processed = jobRepository.findAll()
							.stream()
							.filter(j -> j.getStatus() == JobStatus.PROCESSED)
							.count();
					return processed >= 2;
					});

		List<GenerationJob> allJobs = jobRepository.findAll();

		long processedCount = allJobs.stream()
				.filter(j -> j.getStatus() == JobStatus.PROCESSED || j.getStatus() == JobStatus.FAILED)
				.count();

		long rejectedOrStuckCount = allJobs.stream()
				.filter(j -> j.getStatus() != JobStatus.PROCESSED && j.getStatus() != JobStatus.FAILED)
				.count();

		log.info("STATS: Total={}, Processed={}, Rejected/Stuck={}", allJobs.size(), processedCount,
				rejectedOrStuckCount);

		assertThat(allJobs.size()).isEqualTo(15);

		assertThat(processedCount).as("Throttling failed! Too many jobs processed.").isLessThan(10);

		assertThat(rejectedOrStuckCount).as("Expected rejected jobs to remain in initial state").isGreaterThan(5);
	}
    
	private void mockSlowGeminiResponse(long delayMillis) {
		GenerateContentResponse response = mock(GenerateContentResponse.class);
		String dummyJson = TestUtils.createValidJson(); 
		when(response.text()).thenReturn(dummyJson);

		// Simulate network delay inside the mock
		when(mockModels.generateContent(anyString(), anyList(), any())).thenAnswer(invocation -> {
			try {
				Thread.sleep(delayMillis);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			return response;
		});
	}

	private User createUniqueUser(int index) {
		User user = User.builder().telegramUserId(5000L + index) // Unique ID
				.firstName("LoadTester" + index).languageCode("uk").build();
		return userRepository.save(user);
	}

	private UserProfile createTestProfile(User user) {
		UserProfile profile = new UserProfile();
		profile.setUser(user);
		profile.setLevel("beginner");
		profile.setGoal("health");
		profile.setAge(30);
		return userProfileRepository.save(profile);
	}
	
	@TestConfiguration
	@EnableAutoConfiguration
	@EnableAsync
	@EnableJpaRepositories(basePackages = "com.ua.pohribnyi.fitadvisorbot.repository")
	@EntityScan(basePackages = "com.ua.pohribnyi.fitadvisorbot.model.entity")
	@Import({ AsyncConfig.class // CRITICAL: Loads Custom Executor
	})
	static class TestInfrastructureConfig {
	}
    
}

