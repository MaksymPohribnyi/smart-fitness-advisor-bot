package com.ua.pohribnyi.fitadvisorbot.service.telegram.handler;

import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;

import com.ua.pohribnyi.fitadvisorbot.model.entity.user.User;
import com.ua.pohribnyi.fitadvisorbot.model.entity.user.UserProfile;
import com.ua.pohribnyi.fitadvisorbot.model.enums.UserState;
import com.ua.pohribnyi.fitadvisorbot.service.telegram.FitnessAdvisorBotService;
import com.ua.pohribnyi.fitadvisorbot.service.telegram.MessageService;
import com.ua.pohribnyi.fitadvisorbot.service.telegram.TelegramViewService;
import com.ua.pohribnyi.fitadvisorbot.service.user.UserService;
import com.ua.pohribnyi.fitadvisorbot.service.user.UserSessionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class CommandHandler {

	private final UserService userService;
    private final TelegramViewService viewService;
    private final MessageService messageService;
    private final UserSessionService userSessionService;

    /**
     * Handles global commands that run regardless of state (e.g., /start).
     */
    public void handleGlobalCommand(Message message, User user, FitnessAdvisorBotService bot) {
        Long chatId = message.getChatId();

        if (message.getText().equals("/start")) {
            log.info("Processing global /start for user {}", user.getTelegramUserId());
            
            // Check the user's *true* state
            UserState currentState = userSessionService.findOrCreateSession(user).getState();
            
            if (currentState == UserState.DEFAULT || currentState == UserState.ONBOARDING_COMPLETED) {
                // User is already registered, just send welcome back
            	handleStartForExistingUser(chatId, user, bot);
            } else {
                handleStartForNewUser(chatId, user, bot);
            }
        }
    }

    /**
     * Handles text commands and menu buttons when user is in DEFAULT state.
     */
    public void handleDefaultCommand(Message message, User user, FitnessAdvisorBotService bot) {
        Long chatId = message.getChatId();
        String commandText = message.getText();
        String lang = user.getLanguageCode();

        log.info("Processing DEFAULT command: {} from user: {}", commandText, user.getTelegramUserId());

        SendMessage response = routeDefaultCommand(chatId, commandText, lang, user);
        bot.sendMessage(response);
        
    }
    
	private void handleStartForExistingUser(Long chatId, User user, FitnessAdvisorBotService bot) {
		userSessionService.setState(user, UserState.DEFAULT);
		SendMessage response = viewService.getWelcomeBackMessage(chatId, user.getFirstName());
		bot.sendMessage(response);
	}

	private void handleStartForNewUser(Long chatId, User user, FitnessAdvisorBotService bot) {
		userSessionService.setState(user, UserState.AWAITING_PROFILE_LEVEL);

		SendMessage welcomeMessage = viewService.getOnboardingStartMessage(chatId, user.getFirstName());
		bot.sendMessage(welcomeMessage);

		SendMessage questionMessage = viewService.getOnboardingLevelQuestion(chatId);
		bot.sendMessage(questionMessage);
	}
	
	private SendMessage routeDefaultCommand(Long chatId, String commandText, String lang, User user) {
		try {
			if (commandText.equals(messageService.getMessage("menu.diary", lang))) {
				return handleDiaryCommand(chatId, user);
			} else if (commandText.equals(messageService.getMessage("menu.analytics", lang))) {
				return handleAnalyticsCommand(chatId);
			} else if (commandText.equals(messageService.getMessage("menu.settings", lang))) {
				return handleSettingsCommand(chatId);
			} else if (commandText.equals("/my_profile") || commandText.equals("/activities")) {
				return viewService.getGeneralErrorMessage(chatId);
			} else {
				return viewService.getGeneralErrorMessage(chatId);
			}
		} catch (Exception e) {
			log.error("Error handling command {}: {}", commandText, e.getMessage(), e);
			return viewService.getGeneralErrorMessage(chatId);
		}
	}
	
	private SendMessage handleDiaryCommand(Long chatId, User user) {
        userSessionService.setState(user, UserState.AWAITING_DIARY_MOOD);
        // TODO: Implement diary functionality
        return SendMessage.builder()
                .chatId(chatId.toString())
                .text("Showing Diary... (Not Implemented)")
                .build();
    }

    private SendMessage handleAnalyticsCommand(Long chatId) {
        // TODO: Implement analytics functionality
        return SendMessage.builder()
                .chatId(chatId.toString())
                .text("Showing Analytics... (Not Implemented)")
                .build();
    }

    private SendMessage handleSettingsCommand(Long chatId) {
        // TODO: Implement settings functionality
        return SendMessage.builder()
                .chatId(chatId.toString())
                .text("Showing Settings... (Not Implemented)")
                .build();
    }

}

