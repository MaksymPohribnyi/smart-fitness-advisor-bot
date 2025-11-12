package com.ua.pohribnyi.fitadvisorbot.service.ai;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ua.pohribnyi.fitadvisorbot.model.entity.GenerationJob;
import com.ua.pohribnyi.fitadvisorbot.model.entity.user.User;
import com.ua.pohribnyi.fitadvisorbot.model.entity.user.UserProfile;
import com.ua.pohribnyi.fitadvisorbot.repository.ai.GenerationJobRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class SyntheticDataService {

	private final GenerationJobRepository jobRepository;
	private final GeminiPromptBuilderService promptBuilderService;
	private final GeminiApiClient geminiApiClient; // Worker 1

	/**
	 * This is the main entry point, called synchronously by the Handler. It creates
	 * the Job and triggers the *first* async worker.
	 */
	@Transactional
	public void triggerHistoryGeneration(User user, UserProfile profile, Long chatId, Integer messageId) {
		log.info("Creating PENDING job for user {}", user.getId());

		GenerationJob job = GenerationJob.createPendingJob(user, chatId, messageId);
		jobRepository.save(job); // 'job' now has an ID

		String prompt = promptBuilderService.buildOnboardingPrompt(profile);

		// Call the async worker to do the heavy lifting
		geminiApiClient.generateAndStageHistory(job.getId(), prompt);

		log.info("PENDING Job {} created and async download triggered for user {}", job.getId(), user.getId());
	}
}