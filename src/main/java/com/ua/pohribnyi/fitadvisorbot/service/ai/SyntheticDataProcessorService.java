package com.ua.pohribnyi.fitadvisorbot.service.ai;

import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ua.pohribnyi.fitadvisorbot.model.dto.ParsedData;
import com.ua.pohribnyi.fitadvisorbot.model.dto.google.ActivityDto;
import com.ua.pohribnyi.fitadvisorbot.model.dto.google.DailyMetricDto;
import com.ua.pohribnyi.fitadvisorbot.model.entity.Activity;
import com.ua.pohribnyi.fitadvisorbot.model.entity.DailyMetric;
import com.ua.pohribnyi.fitadvisorbot.model.entity.GenerationJob;
import com.ua.pohribnyi.fitadvisorbot.model.enums.JobStatus;
import com.ua.pohribnyi.fitadvisorbot.repository.ai.GenerationJobRepository;
import com.ua.pohribnyi.fitadvisorbot.repository.data.ActivityRepository;
import com.ua.pohribnyi.fitadvisorbot.repository.data.DailyMetricRepository;
import com.ua.pohribnyi.fitadvisorbot.util.concurrency.event.JobDownloadedEvent;
import com.ua.pohribnyi.fitadvisorbot.util.concurrency.event.JobProcessedEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class SyntheticDataProcessorService {

	private final SyntheticDataWorkerService dataWorkerService;

	/**
	 * Listens for JobDownloadedEvent AFTER Worker 1's transaction commits.
	 * 
	 * TransactionPhase.AFTER_COMMIT guarantees: - job.rawResponse is persisted in
	 * DB - No race conditions with Worker 1
	 * 
	 * @Async: Runs in separate thread (dataProcessingExecutor)
	 */
	@Async("dataProcessingExecutor")
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void handleJobDownloadedEvent(JobDownloadedEvent event) {
		Long jobId = event.getJobId();
		String threadName = Thread.currentThread().getName();

		log.info("[Thread: {}] Received event for job {}", threadName, jobId);

		try {
			// Call the public, proxied method on the new service.
			// This ensures @Transactional(REQUIRES_NEW) is triggered.
			dataWorkerService.processJobInTransaction(jobId);
		} catch (Exception e) {
			log.error("[{}] Failed to process job {}", threadName, jobId);
		}
	}

}