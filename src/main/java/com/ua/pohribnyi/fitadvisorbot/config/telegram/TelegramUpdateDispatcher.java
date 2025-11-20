package com.ua.pohribnyi.fitadvisorbot.config.telegram;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import com.ua.pohribnyi.fitadvisorbot.model.entity.user.User;
import com.ua.pohribnyi.fitadvisorbot.model.enums.UserState;
import com.ua.pohribnyi.fitadvisorbot.service.telegram.FitnessAdvisorBotService;
import com.ua.pohribnyi.fitadvisorbot.service.telegram.TelegramErrorHandler;
import com.ua.pohribnyi.fitadvisorbot.service.telegram.handler.CallbackQueryHandler;
import com.ua.pohribnyi.fitadvisorbot.service.telegram.handler.CommandHandler;
import com.ua.pohribnyi.fitadvisorbot.service.user.UserService;
import com.ua.pohribnyi.fitadvisorbot.service.user.UserSessionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramUpdateDispatcher {

	private final CommandHandler commandHandler;
	private final CallbackQueryHandler callbackQueryHandler;
	private final UserService userService;
	private final UserSessionService userSessionService;
	private final TelegramErrorHandler errorHandler;

	public void dispatch(Update update, FitnessAdvisorBotService bot) {
		try {
			// 1. Get the Telegram User object from the update
			org.telegram.telegrambots.meta.api.objects.User telegramUser = getTelegramUser(update);
			if (telegramUser == null) {
				log.warn("Could not extract user from update: {}", update.getUpdateId());
				return;
			}

			// 2. Find or Create our internal User
			User user = userService.findOrCreateUser(telegramUser);

			// 3. Get the User's *active* state (with timeout logic)
			UserState activeState = userSessionService.getActiveState(user);

			// 4. --- State-Based Routing ---
			if (isGlobalCommand(update)) {
				commandHandler.handleGlobalCommand(update.getMessage(), user, bot);
				return;
			}

			switch (activeState) {
			case ONBOARDING_START:
			case AWAITING_PROFILE_LEVEL:
			case AWAITING_PROFILE_AGE:
			case AWAITING_PROFILE_GOAL:
				// Onboarding flow is handled by CallbackQueryHandler
				if (update.hasCallbackQuery()) {
					callbackQueryHandler.handleOnboardingCallback(update.getCallbackQuery(), user, bot);
				} else {
					// User is typing text when they should be clicking buttons
					log.warn("User {} in state {} sent text, ignoring.", user.getId(), activeState);
					// (Optional: send a message "Please use the buttons")
				}
				break;
			// (Future states for Diary)
			// case AWAITING_DIARY_MOOD:
			// ...

			case DEFAULT:
			case ONBOARDING_COMPLETED:
			default:
				// Normal operation: route to default handlers
				routeDefaultState(update, user, bot);
				break;
			}
		} catch (Exception e) {
			errorHandler.handleGlobalError(e, update, bot);
		}
	}

	/**
	 * Routes updates when the user is in the DEFAULT state.
	 */
	private void routeDefaultState(Update update, User user, FitnessAdvisorBotService bot) {
		if (update.hasMessage() && update.getMessage().hasText()) {
			// Send ALL text (commands AND menu buttons) to the CommandHandler
			commandHandler.handleDefaultCommand(update.getMessage(), user, bot);
		} else if (update.hasCallbackQuery()) {
			// Send to the *default* callback handler (e.g., for Strava, Settings)
			callbackQueryHandler.handleDefaultCallback(update.getCallbackQuery(), user, bot);
		} else {
			log.warn("Received unhandled update type in DEFAULT state: {}", update);
		}
	}

	private boolean isGlobalCommand(Update update) {
		// /start is the only command that works in any state to reset it
		return update.hasMessage() && update.getMessage().isCommand() && update.getMessage().getText().equals("/start");
	}

	private org.telegram.telegrambots.meta.api.objects.User getTelegramUser(Update update) {
		if (update.hasMessage()) {
			return update.getMessage().getFrom();
		} else if (update.hasCallbackQuery()) {
			return update.getCallbackQuery().getFrom();
		}
		return null;
	}
}
