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
import java.util.concurrent.TimeUnit;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
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
import com.ua.pohribnyi.fitadvisorbot.model.entity.Activity;
import com.ua.pohribnyi.fitadvisorbot.model.entity.DailyMetric;
import com.ua.pohribnyi.fitadvisorbot.model.entity.GenerationJob;
import com.ua.pohribnyi.fitadvisorbot.model.entity.user.User;
import com.ua.pohribnyi.fitadvisorbot.model.entity.user.UserProfile;
import com.ua.pohribnyi.fitadvisorbot.repository.ai.GenerationJobRepository;
import com.ua.pohribnyi.fitadvisorbot.repository.data.ActivityRepository;
import com.ua.pohribnyi.fitadvisorbot.repository.data.DailyMetricRepository;
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

@SpringBootTest(classes = {
		GeminiAsyncPipelineIntegrationTest.TestInfrastructureConfig.class, 
		WebConfig.class, // ObjectMapper
		AsyncConfig.class, // ThreadPools (Worker 1 & 2)
		PromptMessageSourceConfig.class, // Завантаження промптів з .yml

		// 2. Сервіси AI Pipeline (Тільки те, що тестуємо)
		SyntheticDataService.class, // Entry point
		GeminiApiClient.class, // Worker 1
		GenerationJobUpdaterService.class, // Job Updates (Transactions)
		SyntheticDataWorkerService.class, // Worker 2 (Processing)
		GeminiPromptBuilderService.class, 
		PromptService.class, 
		GeminiConfigFactory.class, 
		GeminiSchemaDefiner.class,

		// 3. Слухачі подій (Зв'язок між етапами)
		JobCreationListener.class,
		JobDownloadedEventListener.class
})
@Testcontainers
@ActiveProfiles("test")
class GeminiAsyncPipelineIntegrationTest {

    @Autowired
    private SyntheticDataService dataService;
    
    @Autowired
    private GenerationJobRepository jobRepository;
    
    @Autowired private ActivityRepository activityRepository;
   
    @Autowired private DailyMetricRepository dailyMetricRepository;
    
    @Autowired private UserRepository userRepository;

    @Autowired private UserProfileRepository userProfileRepository;
    
    @Autowired
    private ApplicationEventPublisher eventPublisher;
    
    @MockitoBean
    private Client mockGeminiClient;
    
    private Models mockModels;
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
        .withDatabaseName("test")
        .withUsername("test")
        .withPassword("test");
    
    @DynamicPropertySource
    static void registerPgProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.task.execution.pool.core-size", () -> "2");
        registry.add("spring.task.execution.pool.max-size", () -> "4");
        registry.add("spring.task.execution.pool.queue-capacity", () -> "10");
    }
    
	static {
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
	}
    
	@BeforeEach
	void setup() {
		mockModels = mock(Models.class);
		ReflectionTestUtils.setField(mockGeminiClient, "models", mockModels);

		jobRepository.deleteAll();
        activityRepository.deleteAll();
        dailyMetricRepository.deleteAll();
        userProfileRepository.deleteAll();
        userRepository.deleteAll();
	}

    @Test
    @DisplayName("Full pipeline: JobCreatedEvent → Worker1 → JobDownloadedEvent → Worker2")
    void fullAsyncPipeline_successPath() throws Exception {
        // Arrange
        User testUser = createTestUser();
        UserProfile profile = createTestProfile(testUser);

		// Mock Successful API Response
		GenerateContentResponse response = mock(GenerateContentResponse.class);
		when(response.text()).thenReturn(TestUtils.createValidJson());
		when(mockModels.generateContent(anyString(), anyList(), any())).thenReturn(response);

        // Act: Trigger real async flow
        dataService.triggerHistoryGeneration(testUser, profile, 123L, 456);
        
		// Wait for completion (or use Awaitility)
		Awaitility.await()
		.atMost(10, TimeUnit.SECONDS)
		.pollInterval(Duration.ofMillis(200))
				.until(() -> {
					List<GenerationJob> jobs = jobRepository.findAll();
					if (jobs.isEmpty())
						return false;
					GenerationJob job = jobs.get(0);
					if (job.getStatus() == JobStatus.FAILED) {
						throw new RuntimeException("Job failed unexpectedly: " + job.getErrorMessage() + "\nDetails: "
								+ job.getErrorDetails());
					}
					return job.getStatus() == JobStatus.PROCESSED;
				});

        // Assert
        GenerationJob completedJob = jobRepository.findAll().get(0);
        assertThat(completedJob.getStatus()).isEqualTo(JobStatus.PROCESSED);
        assertThat(completedJob.getRawResponse()).isNull(); // Cleaned after processing
        
        // Verify data was saved
        List<Activity> activities = activityRepository.findAll();
        List<DailyMetric> metrics = dailyMetricRepository.findAll();
        assertThat(activities).isNotEmpty();
        assertThat(metrics).isNotEmpty();
    }
    
    @Test
    @DisplayName("Pipeline handles API failure gracefully")
    void fullAsyncPipeline_apiFailure() throws Exception {
		// Arrange
		when(mockModels.generateContent(anyString(), anyList(), any()))
				.thenThrow(new RuntimeException("Google API 429 Too Many Requests"));
    
        User testUser = createTestUser();
        UserProfile profile = createTestProfile(testUser);
        
        // Act
        dataService.triggerHistoryGeneration(testUser, profile, 123L, 456);
        
		// Wait
		Awaitility.await()
		.atMost(10, TimeUnit.SECONDS)
		.until(() -> {
			GenerationJob job = jobRepository.findAll().get(0);
			return job.getStatus() == JobStatus.FAILED;
		});

        // Assert
        GenerationJob failedJob = jobRepository.findAll().get(0);
        assertThat(failedJob.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(failedJob.getErrorMessage()).contains("Too Many Requests");
    }

	private User createTestUser() {
		User user = User.builder()
				.telegramUserId(999L)
				.firstName("TestUser")
				.languageCode("uk")
				.build();
		return userRepository.save(user);
	}

	private UserProfile createTestProfile(User user) {
		UserProfile profile = new UserProfile();
        profile.setUser(user);
        profile.setLevel("beginner");
        profile.setGoal("health");
        profile.setAge(25);
        return userProfileRepository.save(profile);
	}
	
	@TestConfiguration
	@EnableAutoConfiguration // Вмикає JPA, DataSource, TransactionManager, Jackson
	@EnableAsync // Вмикає асинхронність
	@EnableJpaRepositories(basePackages = "com.ua.pohribnyi.fitadvisorbot.repository") // Тільки репозиторії
	@EntityScan(basePackages = "com.ua.pohribnyi.fitadvisorbot.model.entity") // Тільки сутності
	static class TestInfrastructureConfig {
	}

}