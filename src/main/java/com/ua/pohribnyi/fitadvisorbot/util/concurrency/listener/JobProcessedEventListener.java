package com.ua.pohribnyi.fitadvisorbot.util.concurrency.listener;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import com.ua.pohribnyi.fitadvisorbot.enums.JobStatus;
import com.ua.pohribnyi.fitadvisorbot.model.dto.analytics.PeriodReportDto;
import com.ua.pohribnyi.fitadvisorbot.model.entity.GenerationJob;
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
public class JobProcessedEventListener {

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
				handleSuccess(job, chatId, messageId, lang);
			} else if (event.getStatus() == JobStatus.FAILED) {
				handleFailure(job, chatId, messageId, lang);
			}
		} catch (TelegramApiException e) {
			log.error("Failed to deliver job notification for job {}: {}", job.getId(), e.getMessage());
		}

	}
	
	private void handleSuccess(GenerationJob job, Long chatId, Integer messageId, String lang)
			throws TelegramApiException {
		// 1. Delete the "Processing..." loader message to clean up the chat.
		// We delete instead of edit because we need to switch from Inline to Reply
		// keyboard (Main Menu),
		// which is not supported by the EditMessageText API.
		deleteMessageSafely(chatId, messageId);

		// 2. Send a NEW "Success" message with the Main Menu attached.
		String successText = TelegramViewService
				.escapeMarkdownV2(messageService.getMessage("onboarding.job.success", lang));
		SendMessage successMsg = messageBuilder.createMessageWithKeyboard(chatId, successText,
				keyboardBuilder.createMainMenuKeyboard(lang));
		bot.execute(successMsg);

		// 3. Send the generated analytics report as a separate message.
		PeriodReportDto report = analyticsService.generateOnboardingReport(job.getUser());
		SendMessage reportMsg = viewService.getAnalyticsReportMessage(chatId, report);
		bot.sendMessage(reportMsg);
	}

	private void handleFailure(GenerationJob job, Long chatId, Integer messageId, String lang)
			throws TelegramApiException {
		String error = job.getErrorMessage() != null ? job.getErrorMessage() : "Unknown error";
		String text = TelegramViewService
				.escapeMarkdownV2(messageService.getMessage("onboarding.job.failed", lang, error));

		// For failure, editing the existing message is sufficient UX.
		EditMessageText errorMsg = messageBuilder.createEditMessage(chatId, messageId, text);
		bot.execute(errorMsg);
	}

	private void deleteMessageSafely(Long chatId, Integer messageId) {
		try {
			bot.execute(new DeleteMessage(chatId.toString(), messageId));
		} catch (TelegramApiException e) {
			// Message might be too old or already deleted; this is not critical.
			log.debug("Could not delete processing message (id: {}): {}", messageId, e.getMessage());
		}
	}

}
