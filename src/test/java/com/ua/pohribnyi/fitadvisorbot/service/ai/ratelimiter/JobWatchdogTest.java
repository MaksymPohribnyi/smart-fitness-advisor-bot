package com.ua.pohribnyi.fitadvisorbot.service.ai.ratelimiter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.ua.pohribnyi.fitadvisorbot.enums.JobStatus;
import com.ua.pohribnyi.fitadvisorbot.model.entity.GenerationJob;
import com.ua.pohribnyi.fitadvisorbot.model.entity.user.User;
import com.ua.pohribnyi.fitadvisorbot.repository.ai.GenerationJobRepository;

/**
 * Integration tests для JobWatchdog.
 * 
 * Тестує: 1. Cleanup зависших Job 2. Збереження PROCESSED/FAILED Job 3. Cleanup
 * старих Job
 */
@SpringBootTest
class JobWatchdogTest {

	@Autowired
	private JobWatchdog watchdog;

	@Autowired
	private GenerationJobRepository jobRepository;

	private User testUser;

	@BeforeEach
	void setUp() {
		jobRepository.deleteAll();

		testUser = User.builder().telegramUserId(123L).firstName("Test").languageCode("uk").build();
	}

	/**
	 * ═══════════════════════════════════════════════════════════════════ TEST #1:
	 * Watchdog знаходить зависші PENDING Job
	 * ═══════════════════════════════════════════════════════════════════
	 */
	@Test
	@DisplayName("Watchdog маркує PENDING job як FAILED після timeout")
	void watchdog_marksStalledPendingJobAsFailed() {
		// Arrange: Створюємо Job старше 5 хвилин
		GenerationJob oldJob = GenerationJob.createPendingJob(testUser, 123L, 456);
		oldJob.setCreatedAt(Instant.now().minus(Duration.ofMinutes(6)));
		jobRepository.save(oldJob);

		// Act: Запускаємо Watchdog
		watchdog.cleanupStalledJobs();

		// Assert
		GenerationJob updated = jobRepository.findById(oldJob.getId()).orElseThrow();
		assertThat(updated.getStatus()).isEqualTo(JobStatus.FAILED);
		assertThat(updated.getErrorCode()).isEqualTo("JOB_TIMEOUT");
		assertThat(updated.getErrorMessage()).contains("timeout");
	}

	/**
	 * ═══════════════════════════════════════════════════════════════════ TEST #2:
	 * Watchdog НЕ чіпає свіжі PENDING Job
	 * ═══════════════════════════════════════════════════════════════════
	 */
	@Test
	@DisplayName("Watchdog НЕ маркує свіжі PENDING job")
	void watchdog_doesNotTouchRecentPendingJobs() {
		// Arrange: Свіжий Job (1 хвилина)
		GenerationJob freshJob = GenerationJob.createPendingJob(testUser, 123L, 456);
		freshJob.setCreatedAt(Instant.now().minus(Duration.ofMinutes(1)));
		jobRepository.save(freshJob);

		// Act
		watchdog.cleanupStalledJobs();

		// Assert: Job залишається PENDING
		GenerationJob unchanged = jobRepository.findById(freshJob.getId()).orElseThrow();
		assertThat(unchanged.getStatus()).isEqualTo(JobStatus.PENDING);
	}

	/**
	 * ═══════════════════════════════════════════════════════════════════ TEST #3:
	 * Watchdog обробляє DOWNLOADING Job
	 * ═══════════════════════════════════════════════════════════════════
	 */
	@Test
	@DisplayName("Watchdog маркує зависші DOWNLOADING job")
	void watchdog_marksStalledDownloadingJob() {
		// Arrange
		GenerationJob downloadingJob = GenerationJob.createPendingJob(testUser, 123L, 456);
		downloadingJob.setStatus(JobStatus.DOWNLOADING);
		downloadingJob.setCreatedAt(Instant.now().minus(Duration.ofMinutes(10)));
		jobRepository.save(downloadingJob);

		// Act
		watchdog.cleanupStalledJobs();

		// Assert
		GenerationJob failed = jobRepository.findById(downloadingJob.getId()).orElseThrow();
		assertThat(failed.getStatus()).isEqualTo(JobStatus.FAILED);
	}

	/**
	 * ═══════════════════════════════════════════════════════════════════ TEST #4:
	 * Watchdog НЕ чіпає PROCESSED Job
	 * ═══════════════════════════════════════════════════════════════════
	 */
	@Test
	@DisplayName("Watchdog НЕ маркує PROCESSED job як failed")
	void watchdog_doesNotTouchProcessedJobs() {
		// Arrange: Старий PROCESSED job
		GenerationJob processedJob = GenerationJob.createPendingJob(testUser, 123L, 456);
		processedJob.markAsProcessed();
		processedJob.setCreatedAt(Instant.now().minus(Duration.ofHours(1)));
		jobRepository.save(processedJob);

		// Act
		watchdog.cleanupStalledJobs();

		// Assert: Залишається PROCESSED
		GenerationJob unchanged = jobRepository.findById(processedJob.getId()).orElseThrow();
		assertThat(unchanged.getStatus()).isEqualTo(JobStatus.PROCESSED);
	}

	/**
     * ═══════════════════════════════════════════════════════════════════
     * TEST #5: Batch cleanup кількох Job
     * ═══════════════════════════════════════════════════════════════════
     */
    @Test
    @DisplayName("Watchdog обробляє множинні зависші jobs")
    void watchdog_handlesMultipleStalledJobs() {
        // Arrange: 3 зависші Job
        for (int i = 0; i < 3; i++) {
            GenerationJob job = GenerationJob.createPendingJob(testUser, 123L, i);
            job.setCreatedAt(Instant.now().minus(Duration.ofMinutes(6)));
            jobRepository.save(job);
        }

        // 1 свіжий Job
        GenerationJob freshJob = GenerationJob.createPendingJob(testUser, 123L, 999);
        jobRepository.save(freshJob);

        // Act
        watchdog.cleanupStalledJobs();

        // Assert
        List<GenerationJob> allJobs = jobRepository.findAll();
        long failedCount = allJobs.stream()
            .filter(j -> j.getStatus() == JobStatus.FAILED)
            .count();
        long pendingCount = allJobs.stream()
            .filter(j -> j.getStatus() == JobStatus.PENDING)
            .count();

        assertThat(failedCount).isEqualTo(3);
        assertThat(pendingCount).isEqualTo(1);
    }

	/**
	 * ═══════════════════════════════════════════════════════════════════ TEST #6:
	 * Cleanup старих PROCESSED Job
	 * ═══════════════════════════════════════════════════════════════════
	 */
	@Test
	@DisplayName("Cleanup видаляє PROCESSED jobs старше 7 днів")
	void cleanup_deletesOldProcessedJobs() {
		// Arrange: Старий PROCESSED job (8 днів)
		GenerationJob oldProcessed = GenerationJob.createPendingJob(testUser, 123L, 1);
		oldProcessed.markAsProcessed();
		oldProcessed.setCompletedAt(Instant.now().minus(Duration.ofDays(8)));
		jobRepository.save(oldProcessed);

		// Свіжий PROCESSED job (3 дні)
		GenerationJob recentProcessed = GenerationJob.createPendingJob(testUser, 123L, 2);
		recentProcessed.markAsProcessed();
		recentProcessed.setCompletedAt(Instant.now().minus(Duration.ofDays(3)));
		jobRepository.save(recentProcessed);

		// Act
		watchdog.cleanupOldJobs();

		// Assert: Старий видалений, свіжий залишився
		List<GenerationJob> remaining = jobRepository.findAll();
		assertThat(remaining).hasSize(1);
		assertThat(remaining.get(0).getId()).isEqualTo(recentProcessed.getId());
	}

	/**
	 * ═══════════════════════════════════════════════════════════════════ TEST #7:
	 * Cleanup НЕ видаляє PENDING Job
	 * ═══════════════════════════════════════════════════════════════════
	 */
	@Test
	@DisplayName("Cleanup НЕ видаляє активні PENDING jobs")
	void cleanup_doesNotDeleteActivePendingJobs() {
		// Arrange: Старий PENDING job
		GenerationJob oldPending = GenerationJob.createPendingJob(testUser, 123L, 1);
		oldPending.setCreatedAt(Instant.now().minus(Duration.ofDays(10)));
		jobRepository.save(oldPending);

		// Act
		watchdog.cleanupOldJobs();

		// Assert: PENDING НЕ видалений
		List<GenerationJob> remaining = jobRepository.findAll();
		assertThat(remaining).hasSize(1);
	}

}