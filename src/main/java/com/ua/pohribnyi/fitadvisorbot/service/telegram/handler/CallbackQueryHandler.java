package com.ua.pohribnyi.fitadvisorbot.service.telegram.handler;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import com.ua.pohribnyi.fitadvisorbot.enums.JobStatus;
import com.ua.pohribnyi.fitadvisorbot.enums.UserState;
import com.ua.pohribnyi.fitadvisorbot.model.entity.GenerationJob;
import com.ua.pohribnyi.fitadvisorbot.model.entity.user.User;
import com.ua.pohribnyi.fitadvisorbot.model.entity.user.UserProfile;
import com.ua.pohribnyi.fitadvisorbot.repository.ai.GenerationJobRepository;
import com.ua.pohribnyi.fitadvisorbot.repository.user.UserProfileRepository;
import com.ua.pohribnyi.fitadvisorbot.service.ai.GeminiApiClient;
import com.ua.pohribnyi.fitadvisorbot.service.ai.GeminiPromptBuilderService;
import com.ua.pohribnyi.fitadvisorbot.service.ai.SyntheticDataService;
import com.ua.pohribnyi.fitadvisorbot.service.telegram.FitnessAdvisorBotService;
import com.ua.pohribnyi.fitadvisorbot.service.telegram.MessageBuilderService;
import com.ua.pohribnyi.fitadvisorbot.service.telegram.MessageService;
import com.ua.pohribnyi.fitadvisorbot.service.telegram.TelegramViewService;
import com.ua.pohribnyi.fitadvisorbot.service.user.UserSessionService;
import com.ua.pohribnyi.fitadvisorbot.util.KeyboardBuilderService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class CallbackQueryHandler {

	private final TelegramViewService viewService;
	private final UserSessionService userSessionService;
	private final UserProfileRepository userProfileRepository;
	private final SyntheticDataService syntheticDataService;
	private final GenerationJobRepository jobRepository;
	private final GeminiApiClient geminiApiClient;
	private final GeminiPromptBuilderService promptBuilderService;
	private final MessageService messageService;
	private final MessageBuilderService messageBuilder;

	/**
	 * Handles callbacks when user is in the DEFAULT state (e.g., Strava buttons).
	 */
	public void handleDefaultCallback(CallbackQuery callbackQuery, User user, FitnessAdvisorBotService bot) {
		log.warn("Received unhandled callback in DEFAULT state: {}", callbackQuery.getData());
		answerCallback(callbackQuery.getId(), bot);
	}

	/**
	 * Handles callbacks during the Onboarding flow. This is our state machine for
	 * the poll.
	 */
	public void handleOnboardingCallback(CallbackQuery callbackQuery, User user, FitnessAdvisorBotService bot) {
		String data = callbackQuery.getData();
		Long chatId = callbackQuery.getMessage().getChatId();
		Integer messageId = callbackQuery.getMessage().getMessageId();

		try {
			if (data.startsWith("onboarding:level:")) {
				handleLevelSelection(data, chatId, messageId, user, bot);
			} else if (data.startsWith("onboarding:goal:")) {
				handleGoalSelection(data, chatId, messageId, user, bot);
			} else if (data.startsWith("onboarding:age:")) { 
		        handleAgeSelection(data, chatId, messageId, user, bot);
			} else if (data.startsWith("job:retry:")) {
				handleJobRetry(data, chatId, messageId, user, bot);
			} else {
				log.warn("Unknown onboarding callback: {}", data);
			}
		} catch (Exception e) {
			log.error("Error handling onboarding callback {}: {}", data, e.getMessage(), e);
			bot.sendMessage(viewService.getGeneralErrorMessage(chatId));
		} finally {
			answerCallback(callbackQuery.getId(), bot);
		}

	}

	private void handleLevelSelection(String data, Long chatId, Integer messageId, User user,
			FitnessAdvisorBotService bot) throws TelegramApiException {
		String level = extractValue(data);

		saveProfileLevel(user, level);
		userSessionService.setState(user, UserState.AWAITING_PROFILE_GOAL);

		EditMessageText nextQuestion = viewService.getOnboardingGoalQuestion(chatId, messageId);
		bot.execute(nextQuestion);

		log.info("User {} selected level: {}", user.getId(), level);
	}

	private void handleGoalSelection(String data, Long chatId, Integer messageId, User user,
			FitnessAdvisorBotService bot) throws TelegramApiException {
		String goal = extractValue(data);
		saveProfileGoal(user, goal);

		userSessionService.setState(user, UserState.AWAITING_PROFILE_AGE);
		EditMessageText ageQuestion = viewService.getOnboardingAgeQuestion(chatId, messageId);
		bot.execute(ageQuestion);

		log.info("User {} selected goal: {}. Asking for age.", user.getId(), goal);

	}
	
	private void handleAgeSelection(String data, Long chatId, Integer messageId, User user,
			FitnessAdvisorBotService bot) throws TelegramApiException {
		Integer age = Integer.parseInt(extractValue(data));
	    
	    UserProfile profile = saveProfileAge(user, age);
	    userSessionService.setState(user, UserState.ONBOARDING_COMPLETED);
	    deleteMessage(chatId, messageId, bot);

	    try {
			Message sentMessage = bot.executeAndReturn(viewService.getGenerationWaitMessage(chatId));
			syntheticDataService.triggerHistoryGeneration(user, profile, chatId, sentMessage.getMessageId());
		} catch (RuntimeException e) {
			if (e.getMessage().contains("overloaded")) {
				log.warn("System overload for user {}: {}", user.getId(), e.getMessage());
				String errorText = TelegramViewService
						.escapeMarkdownV2(messageService.getMessage("error.system.overloaded", user.getLanguageCode()));
				SendMessage errorMsg = messageBuilder.createMessage(chatId, errorText);
				bot.sendMessage(errorMsg);

			} else {
				log.error("Unexpected error during history generation for user {}", user.getId(), e);
				bot.sendMessage(viewService.getGeneralErrorMessage(chatId));
			}
		}
	}
	
	private String extractValue(String data) {
		String[] parts = data.split(":");
		return parts.length > 2 ? parts[2] : "";
	}

	@Transactional
	private void saveProfileLevel(User user, String level) {
		UserProfile profile = userProfileRepository.findByUser(user).orElse(new UserProfile());
		profile.setUser(user);
		profile.setLevel(level);
		userProfileRepository.save(profile);
	}

	@Transactional
	private UserProfile saveProfileGoal(User user, String goal) {
		UserProfile profile = userProfileRepository.findByUser(user)
				.orElseThrow(() -> new IllegalStateException("UserProfile not found for user " + user.getId()));
		profile.setGoal(goal);
		return userProfileRepository.save(profile);
	}

	@Transactional
	private UserProfile saveProfileAge(User user, int age) {
		UserProfile profile = userProfileRepository.findByUser(user)
				.orElseThrow(() -> new IllegalStateException("UserProfile not found for user " + user.getId()));
		profile.setAge(age);
		return userProfileRepository.save(profile);
	}
	
	@Transactional
	private void handleJobRetry(String data, Long chatId, Integer messageId, User user, FitnessAdvisorBotService bot) {
		Long jobId = Long.parseLong(data.split(":")[2]);
		String lang = user.getLanguageCode();

		GenerationJob job = jobRepository.findById(jobId)
				.orElseThrow(() -> new IllegalStateException("Job not found for retry: " + jobId));

		// 1. Редагуємо повідомлення про помилку назад на "В процесі"
		String text = messageService.getMessage("onboarding.job.started", lang);
		EditMessageText waitMsg = messageBuilder.createEditMessage(chatId, messageId, text);
		// Прибираємо клавіатуру "Повторити"
		waitMsg.setReplyMarkup(null);

		try {
			bot.execute(waitMsg);
		} catch (TelegramApiException e) {
			log.warn("Failed to edit message for retry: {}", e.getMessage());
		}

		// 2. Очищуємо статус помилки і готуємо до повторного запуску
		job.setStatus(JobStatus.PENDING);
		job.setErrorMessage(null);
		job.setErrorDetails(null);
		job.setErrorCode(null);
		jobRepository.save(job); // Зберігаємо PENDING

		// 3. Отримуємо профіль і запускаємо АПІ
		UserProfile profile = userProfileRepository.findByUser(user)
				.orElseThrow(() -> new IllegalStateException("Profile not found for retry"));

		String prompt = promptBuilderService.buildOnboardingPrompt(profile);

		// Запускаємо воркер 1 (Gemini) знову
		geminiApiClient.generateAndStageHistory(job.getId(), prompt);

		log.info("Retrying job {} for user {}", jobId, user.getId());
	}

	private void deleteMessage(Long chatId, Integer messageId, FitnessAdvisorBotService bot) {
		try {
			bot.execute(DeleteMessage.builder().chatId(chatId).messageId(messageId).build());
		} catch (Exception e) {
			log.warn("Failed to delete message {}: {}", messageId, e.getMessage());
		}
	}

	private void answerCallback(String callbackQueryId, FitnessAdvisorBotService bot) {
		try {
			bot.execute(AnswerCallbackQuery.builder().callbackQueryId(callbackQueryId).build());
		} catch (Exception e) {
			log.error("Failed to answer callback query: {}", e.getMessage());
		}
	}
}