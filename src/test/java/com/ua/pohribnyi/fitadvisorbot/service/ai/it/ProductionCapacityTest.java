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
		ProductionCapacityTest.TestInfrastructureConfig.class, 
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
class ProductionCapacityTest {

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
			.withDatabaseName("stress_db")
			.withUsername("test")
			.withPassword("test");

	@DynamicPropertySource
	static void registerProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);

		registry.add("spring.task.execution.pool.ai.core-size", () -> "5");
        registry.add("spring.task.execution.pool.ai.max-size", () -> "10");
        registry.add("spring.task.execution.pool.ai.queue-capacity", () -> "100"); 
        registry.add("spring.task.execution.pool.ai.thread-name", () -> "ai-gen-test-"); 
        
        registry.add("spring.task.execution.pool.data.core-size", () -> "5");
        registry.add("spring.task.execution.pool.data.max-size", () -> "15");
        registry.add("spring.task.execution.pool.data.queue-capacity", () -> "500");
        registry.add("spring.task.execution.pool.data.thread-name", () -> "data-proc-test-");

		registry.add("spring.datasource.hikari.maximum-pool-size", () -> "20");
		registry.add("spring.datasource.hikari.connection-timeout", () -> "20000");

		registry.add("resilience4j.ratelimiter.instances.geminiApi.limit-for-period", () -> "500");
		registry.add("resilience4j.ratelimiter.instances.geminiApi.limit-refresh-period", () -> "1s");
	}

	static {
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
	}

	@BeforeEach
	void setUp() {
		jobRepository.deleteAll();
		userProfileRepository.deleteAll();
		userRepository.deleteAll();

		mockModels = mock(Models.class);
		ReflectionTestUtils.setField(mockGeminiClient, "models", mockModels);
	}

	@Test
	@DisplayName("Prod Capacity: 100 users click concurrently -> All accepted (No Rejections)")
	void handle100ConcurrentUsers_Success() throws InterruptedException {
		mockGeminiResponse(50);

		int totalUsers = 100;
		CountDownLatch latch = new CountDownLatch(totalUsers);

		AtomicInteger successCount = new AtomicInteger(0);
		AtomicInteger errorCount = new AtomicInteger(0);

		ExecutorService clientExecutor = Executors.newFixedThreadPool(100);

		log.info("🚀 Launching 100 concurrent requests...");
		for (int i = 0; i < totalUsers; i++) {
			final int idx = i;
			clientExecutor.submit(() -> {
				try {
					User user = createUniqueUser(idx);
					UserProfile profile = createTestProfile(user);

					dataService.triggerHistoryGeneration(user, profile, 1000L + idx, 999);

					successCount.incrementAndGet();
				} catch (Exception e) {
					log.error("Request failed for user {}: {}", idx, e.getMessage());
					errorCount.incrementAndGet();
				} finally {
					latch.countDown();
				}
			});
		}

		latch.await(20, TimeUnit.SECONDS);
		clientExecutor.shutdown();

		log.info("🏁 Result: Success={}, Errors={}", successCount.get(), errorCount.get());

		assertThat(errorCount.get()).as("Unexpected errors occurred").isEqualTo(0);
		assertThat(successCount.get()).as("All 100 requests should be accepted").isEqualTo(100);

		assertThat(jobRepository.count()).isEqualTo(100);
		
		log.info("⏳ Waiting for async workers to complete processing...");
		Awaitility.await()
			.atMost(15, TimeUnit.SECONDS)
			.pollInterval(Duration.ofMillis(500))
			.until(() -> {
				List<GenerationJob> jobs = jobRepository.findAll();
				long failedCount = jobs.stream().filter(j -> j.getStatus() == JobStatus.FAILED).count();
                if (failedCount > 0) {
                    GenerationJob failedJob = jobs.stream()
                        .filter(j -> j.getStatus() == JobStatus.FAILED)
                        .findFirst().orElseThrow();
                    throw new RuntimeException("Test failed! Job " + failedJob.getId() + 
                        " failed with error: " + failedJob.getErrorMessage() + 
                        ". Details: " + failedJob.getErrorDetails());
                }
                long processedCount = jobs.stream()
                        .filter(j -> j.getStatus() == JobStatus.PROCESSED)
                        .count();
				log.info("Progress: {}/{} processed", processedCount, totalUsers);
				return processedCount == totalUsers;
		});
	}

	private void mockGeminiResponse(long delayMillis) {
		GenerateContentResponse response = mock(GenerateContentResponse.class);
		String validJson = TestUtils.createValidJson();
		when(response.text()).thenReturn(validJson);
		when(mockModels.generateContent(anyString(), anyList(), any())).thenAnswer(inv -> {
			Thread.sleep(delayMillis);
			return response;
		});
	}

	private synchronized User createUniqueUser(int index) {
		User user = User.builder().telegramUserId(10000L + index).firstName("U" + index).languageCode("uk").build();
		return userRepository.save(user);
	}

	private synchronized UserProfile createTestProfile(User user) {
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
	@Import({ AsyncConfig.class })
	static class TestInfrastructureConfig {
	}
}
