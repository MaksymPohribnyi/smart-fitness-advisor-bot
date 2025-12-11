package com.ua.pohribnyi.fitadvisorbot.util.concurrency.listener;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.ua.pohribnyi.fitadvisorbot.service.analytics.diary.DailyAdviceWorker;
import com.ua.pohribnyi.fitadvisorbot.util.concurrency.event.DailyAdviceJobSubmittedEvent;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class DailyAdviceJobListener {

	private final DailyAdviceWorker processingService;
	
	
	/**
	 * Triggered AFTER the transaction in DiaryService commits. Ensures the Job
	 * actually exists in DB before we try to process it.
	 */
	@Async("aiGenerationExecutor")
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void handleJobSubmitted(DailyAdviceJobSubmittedEvent event) {
		processingService.processJob(event.getJobId());
	}
	
}
