package com.ua.pohribnyi.fitadvisorbot.service.ai;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import com.ua.pohribnyi.fitadvisorbot.model.dto.analytics.PeriodReportDto;
import com.ua.pohribnyi.fitadvisorbot.model.entity.GenerationJob;
import com.ua.pohribnyi.fitadvisorbot.model.enums.JobStatus;
import com.ua.pohribnyi.fitadvisorbot.repository.ai.GenerationJobRepository;
import com.ua.pohribnyi.fitadvisorbot.service.analytics.FitnessAnalyticsService;
import com.ua.pohribnyi.fitadvisorbot.service.telegram.FitnessAdvisorBotService;
import com.ua.pohribnyi.fitadvisorbot.service.telegram.MessageBuilderService;
import com.ua.pohribnyi.fitadvisorbot.service.telegram.MessageService;
import com.ua.pohribnyi.fitadvisorbot.service.telegram.TelegramViewService;
import com.ua.pohribnyi.fitadvisorbot.util.KeyboardBuilderService;
import com.ua.pohribnyi.fitadvisorbot.util.concurrency.event.JobProcessedEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class JobNotificationService {

	private final GenerationJobRepository jobRepository;
	private final FitnessAdvisorBotService bot; 
	private final TelegramViewService viewService;
	private final MessageService messageService;
	private final MessageBuilderService messageBuilder;
	private final KeyboardBuilderService keyboardBuilder;
	private final FitnessAnalyticsService analyticsService;
	
	/**
	 * Listens for the completion of a job processing task.
	 * Fires AFTER the worker's transaction has committed.
	 * This method is non-transactional.
	 */
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void handleJobProcessed(JobProcessedEvent event) {
		log.info("Received JobProcessedEvent for job {}, status: {}", event.getJobId(), event.getStatus());

		GenerationJob job = jobRepository.findById(event.getJobId())
				.orElseThrow(() -> new IllegalStateException("Job not found: " + event.getJobId()));

		Long chatId = job.getUserChatId();
		Integer messageId = job.getNotificationMessageId();
		String lang = job.getUser().getLanguageCode();

		if (chatId == null || messageId == null) {
			log.warn("Job {} has no chatId or messageId, cannot notify user.", job.getId());
			return;
		}

		try {
			if (event.getStatus() == JobStatus.PROCESSED) {
				// SUCCESS
				String text = TelegramViewService.escapeMarkdownV2(messageService.getMessage("onboarding.job.success", lang));
				EditMessageText successMsg = messageBuilder.createEditMessage(chatId, messageId, text);
				bot.execute(successMsg); 

                PeriodReportDto report = analyticsService.generateOnboardingReport(job.getUser());
                SendMessage reportMsg = viewService.getAnalyticsReportMessage(chatId, report);
                bot.sendMessage(reportMsg);

			} else if (event.getStatus() == JobStatus.FAILED) {
				// FAILURE
				String error = job.getErrorMessage() != null ? job.getErrorMessage() : "Unknown error";
				String text = TelegramViewService.escapeMarkdownV2(messageService.getMessage("onboarding.job.failed", lang, error));

				// Add the "Retry" button
				InlineKeyboardMarkup retryKeyboard = keyboardBuilder.createJobRetryKeyboard(lang, job.getId());
				EditMessageText errorMsg = messageBuilder.createEditMessageWithKeyboard(chatId, messageId, text,
						retryKeyboard);
				bot.execute(errorMsg);
			}
		} catch (TelegramApiException e) {
			log.error("Failed to push notification for job {}: {}", job.getId(), e.getMessage());
		}
	}

}
