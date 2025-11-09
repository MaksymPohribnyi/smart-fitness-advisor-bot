package com.ua.pohribnyi.fitadvisorbot.service.telegram.handler;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import com.ua.pohribnyi.fitadvisorbot.model.entity.user.User;
import com.ua.pohribnyi.fitadvisorbot.model.entity.user.UserProfile;
import com.ua.pohribnyi.fitadvisorbot.model.enums.UserState;
import com.ua.pohribnyi.fitadvisorbot.repository.user.UserProfileRepository;
import com.ua.pohribnyi.fitadvisorbot.service.ai.SyntheticDataService;
import com.ua.pohribnyi.fitadvisorbot.service.telegram.FitnessAdvisorBotService;
import com.ua.pohribnyi.fitadvisorbot.service.telegram.TelegramViewService;
import com.ua.pohribnyi.fitadvisorbot.service.user.UserService;
import com.ua.pohribnyi.fitadvisorbot.service.user.UserSessionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class CallbackQueryHandler {

	private final TelegramViewService viewService;
	private final UserService userService;
	private final UserSessionService userSessionService;
	private final UserProfileRepository userProfileRepository;
	private final SyntheticDataService syntheticDataService;

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

	private void handleLevelSelection(String data, Long chatId, Integer messageId, User user, FitnessAdvisorBotService bot) throws TelegramApiException {
        String level = extractValue(data);
        
        saveProfileLevel(user, level);
        userSessionService.setState(user, UserState.AWAITING_PROFILE_GOAL);
        
        EditMessageText nextQuestion = viewService.getOnboardingGoalQuestion(chatId, messageId);
        bot.execute(nextQuestion);
        
        log.info("User {} selected level: {}", user.getId(), level);
    }

    private void handleGoalSelection(String data, Long chatId, Integer messageId, User user, FitnessAdvisorBotService bot) {
        String goal = extractValue(data);

		UserProfile profile = saveProfileGoal(user, goal);
		userSessionService.setState(user, UserState.ONBOARDING_COMPLETED);

		// 2. This call is fast. It just creates a PENDING job.
		syntheticDataService.triggerHistoryGeneration(user, profile); // Now 'profile' exists

		log.info("Triggered async history generation for user {}", user.getId());

		deleteMessage(chatId, messageId, bot);
		SendMessage finalMessage = viewService.getOnboardingCompletedMessage(chatId);
		log.info("Onboarding completed for user {}. Goal: {}", user.getId(), goal);
		bot.sendMessage(finalMessage);
	}

    private String extractValue(String data) {
        String[] parts = data.split(":");
        return parts.length > 2 ? parts[2] : "";
    }

    @Transactional
    private void saveProfileLevel(User user, String level) {
        UserProfile profile = userProfileRepository.findByUser(user)
                .orElse(new UserProfile());
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

    private void deleteMessage(Long chatId, Integer messageId, FitnessAdvisorBotService bot) {
        try {
            bot.execute(DeleteMessage.builder()
                    .chatId(chatId)
                    .messageId(messageId)
                    .build());
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