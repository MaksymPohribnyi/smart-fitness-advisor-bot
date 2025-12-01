package com.ua.pohribnyi.fitadvisorbot.service.ai.ratelimiter;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ua.pohribnyi.fitadvisorbot.enums.JobStatus;
import com.ua.pohribnyi.fitadvisorbot.model.entity.GenerationJob;
import com.ua.pohribnyi.fitadvisorbot.repository.ai.GenerationJobRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Watchdog працює як CRON task: - Запускається кожні 2 хвилини - Сканує БД на
 * "зависші" Job - Маркує їх як FAILED - Логує для debugging
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobWatchdog {

	private final GenerationJobRepository jobRepository;

	private static final Duration TIMEOUT = Duration.ofMinutes(5);

	/**
	 * ═══════════════════════════════════════════════════════════════════ TASK #1:
	 * Cleanup зависших Job
	 * ═══════════════════════════════════════════════════════════════════
	 * 
	 * Запускається кожні 2 хвилини (fixedRate = 120_000 ms) Затримка старту: 1
	 * хвилина після запуску додатку (initialDelay)
	 * 
	 * ЩО РОБИТЬ: 1. Знаходить Job старше 5 хвилин в проміжних станах 2. Маркує їх
	 * як FAILED 3. Зберігає error details для debugging
	 */
	@Scheduled(fixedRate = 120_000, initialDelay = 60_000)
	@Transactional // CRITICAL: Весь метод в одній транзакції
	public void cleanupStalledJobs() {
		// Cutoff time = 5 хвилин тому
		Instant cutoff = Instant.now().minus(TIMEOUT);

		log.debug("🔍 Scanning for stalled jobs (cutoff: {})", cutoff);

		// Знаходимо всі "підозрілі" Job
		List<GenerationJob> stalled = jobRepository.findAll().stream().filter(job -> isStalled(job, cutoff)).toList();

		// Якщо все OK → просто логуємо та виходимо
		if (stalled.isEmpty()) {
			log.debug("✅ No stalled jobs found");
			return;
		}

		// ALERT: Знайдені зависші Job
		log.warn("🚨 Found {} stalled jobs!", stalled.size());

		// Обробляємо кожен зависший Job
		stalled.forEach(job -> {
			// Детальне логування для debugging
			log.error("Job {} stalled in state {} for >5min. User: {}, Created: {}", job.getId(), job.getStatus(),
					job.getUser().getTelegramUserId(), job.getCreatedAt());

			// Маркуємо як FAILED з детальним описом
			job.markAsFailed("JOB_TIMEOUT", // Error code для моніторингу
					"Processing timeout exceeded (5 minutes)", // User-friendly message
					String.format(
							"Job stuck in %s state. " + "Created at: %s, " + "User: %s, "
									+ "Possible causes: thread pool saturation, API timeout, application restart",
							job.getStatus(), job.getCreatedAt(), job.getUser().getTelegramUserId()));
		});

		// Зберігаємо всі зміни одним batch запитом
		jobRepository.saveAll(stalled);

		log.info("✅ Marked {} stalled jobs as FAILED", stalled.size());
	}

	/**
	 * ═══════════════════════════════════════════════════════════════════ HELPER:
	 * Перевірка чи Job зависший
	 * ═══════════════════════════════════════════════════════════════════
	 * 
	 * Job вважається зависшим якщо: 1. Він в проміжному стані
	 * (PENDING/DOWNLOADING/PROCESSING) 2. Його createdAt старше 5 хвилин
	 * 
	 * ЧИ НЕ зависші стани: - PROCESSED → Job завершений успішно - FAILED → Job вже
	 * маркований як failed - DOWNLOADED → може бути в черзі Worker 2
	 */
	private boolean isStalled(GenerationJob job, Instant cutoff) {
		// Фільтруємо тільки проміжні стани
		boolean isIntermediateState = job.getStatus() == JobStatus.PENDING || job.getStatus() == JobStatus.DOWNLOADING
				|| job.getStatus() == JobStatus.PROCESSING;

		// Перевіряємо час створення
		boolean isTooOld = job.getCreatedAt().isBefore(cutoff);

		return isIntermediateState && isTooOld;
	}

	/**
	 * ═══════════════════════════════════════════════════════════════════ TASK #2:
	 * Cleanup старих completed Job
	 * ═══════════════════════════════════════════════════════════════════
	 * 
	 * Запускається щоночі о 02:00 (cron = "0 0 2 * * *")
	 * 
	 * НАВІЩО? - PROCESSED/FAILED Job зберігають history - Через місяць таблиця
	 * роздувається до 1M+ записів - Cleanup зберігає тільки останні 7 днів
	 * 
	 * ЩО ВИДАЛЯЄТЬСЯ: - PROCESSED jobs старше 7 днів (дані вже збережені в
	 * activities/metrics) - FAILED jobs старше 7 днів (debug info вже не
	 * актуальний)
	 * 
	 * ЩО НЕ ВИДАЛЯЄТЬСЯ: - PENDING/DOWNLOADING/PROCESSING (можуть бути активними)
	 */
	@Scheduled(cron = "0 0 2 * * *") // Кожну ніч о 2:00 AM
	@Transactional
	public void cleanupOldJobs() {
		log.info("🗑️ Starting nightly job cleanup...");

		// Видаляємо Job старше 7 днів
		Instant cutoff = Instant.now().minus(Duration.ofDays(7));

		List<GenerationJob> oldJobs = jobRepository.findAll().stream()
				// Тільки завершені Job
				.filter(job -> job.getStatus() == JobStatus.PROCESSED || job.getStatus() == JobStatus.FAILED)
				// Тільки старі
				.filter(job -> job.getCompletedAt() != null && job.getCompletedAt().isBefore(cutoff)).toList();

		if (!oldJobs.isEmpty()) {
			jobRepository.deleteAll(oldJobs);
			log.info("✅ Deleted {} old jobs (>7 days)", oldJobs.size());
		} else {
			log.debug("✅ No old jobs to cleanup");
		}
	}
}