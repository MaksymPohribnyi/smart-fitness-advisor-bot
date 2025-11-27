package com.ua.pohribnyi.fitadvisorbot.service.ai.ratelimiter;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ua.pohribnyi.fitadvisorbot.model.entity.GenerationJob;
import com.ua.pohribnyi.fitadvisorbot.model.enums.JobStatus;
import com.ua.pohribnyi.fitadvisorbot.repository.ai.GenerationJobRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Watchdog для cleanup "зависших" Job.
 * 
 * Проблема: Job може застрягти якщо: - Thread pool переповнений - Application
 * restart під час виконання - Unhandled exception
 * 
 * Рішення: Перевірка кожні 2 хвилини.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobWatchdog {

	private final GenerationJobRepository jobRepository;
	private static final Duration TIMEOUT = Duration.ofMinutes(5);

	@Scheduled(fixedRate = 120_000, initialDelay = 60_000)
	@Transactional
	public void cleanupStalledJobs() {
		Instant cutoff = Instant.now().minus(TIMEOUT);

		List<GenerationJob> stalled = jobRepository.findAll().stream()
				.filter(job -> isStalled(job, cutoff))
				.toList();

		if (stalled.isEmpty()) {
			return;
		}

		log.warn("Found {} stalled jobs", stalled.size());

		stalled.forEach(job -> {
			log.error("Job {} stalled in {} for >5min", job.getId(), job.getStatus());
			job.markAsFailed("JOB_TIMEOUT", "Processing timeout exceeded", null);
		});

		jobRepository.saveAll(stalled);
	}

	private boolean isStalled(GenerationJob job, Instant cutoff) {
		return (job.getStatus() == JobStatus.PENDING || job.getStatus() == JobStatus.DOWNLOADING
				|| job.getStatus() == JobStatus.PROCESSING) && job.getCreatedAt().isBefore(cutoff);
	}
}