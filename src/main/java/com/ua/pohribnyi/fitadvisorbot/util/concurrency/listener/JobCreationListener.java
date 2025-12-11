package com.ua.pohribnyi.fitadvisorbot.util.concurrency.listener;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.ua.pohribnyi.fitadvisorbot.service.ai.GeminiApiClient;
import com.ua.pohribnyi.fitadvisorbot.util.concurrency.event.JobCreatedEvent;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class JobCreationListener {
    
	private final GeminiApiClient geminiApiClient; 
	
	@Async("aiGenerationExecutor")
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onJobCreated(JobCreatedEvent event) {
		geminiApiClient.generateAndStageHistory(event.getJobId(), event.getPrompt());
	}
}