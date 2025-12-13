package com.ua.pohribnyi.fitadvisorbot.service.user;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;

import com.ua.pohribnyi.fitadvisorbot.enums.UserState;
import com.ua.pohribnyi.fitadvisorbot.model.entity.user.User;
import com.ua.pohribnyi.fitadvisorbot.model.entity.user.UserProfile;
import com.ua.pohribnyi.fitadvisorbot.repository.user.UserProfileRepository;
import com.ua.pohribnyi.fitadvisorbot.service.telegram.TelegramViewService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class SettingsService {

	private final UserProfileRepository userProfileRepository;
	private final TelegramViewService viewService;
	private final UserSessionService sessionService;
	private final UserService userService;

	/**
     * Entry point: Sends a NEW message with the settings menu.
     * Sets user state to SETTINGS_EDITING.
     */
	@Transactional(readOnly = true)
	public SendMessage openSettings(User user) {
		sessionService.setState(user, UserState.SETTINGS_EDITING);
		return createSettingsView(user);
	}

	/**
     * Refreshes the existing settings message (e.g. after connecting Strava).
     */
	@Transactional(readOnly = true)
	public EditMessageText refreshSettings(User user, Integer messageId) {
		return createSettingsEditView(user, messageId);
	}

	
	/**
     * Transitions the UI to the specific editor (Goal, Level, Age).
     */
    public EditMessageText startEditing(User user, Integer messageId, String field) {
        Long chatId = user.getTelegramUserId();
        return switch (field) {
            case "goal" -> viewService.getSettingsGoalEditMessage(chatId, messageId);
            case "level" -> viewService.getSettingsLevelEditMessage(chatId, messageId);
            default -> createSettingsEditView(user, messageId);
        };
    }

    /**
     * Saves the new GOAL and returns to the main settings menu.
     */
    @Transactional
    public EditMessageText updateGoalAndReturn(User user, String newGoal, Integer messageId) {
        UserProfile profile = getProfileOrThrow(user);
        profile.setGoal(newGoal);
        userProfileRepository.save(profile);
        
        return createSettingsEditView(user, messageId);
    }

	/**
	 * Saves the new LEVEL and returns to the main settings menu.
	 */
	@Transactional
	public EditMessageText updateLevelAndReturn(User user, String newLevel, Integer messageId) {
		UserProfile profile = getProfileOrThrow(user);
		profile.setLevel(newLevel);
		userProfileRepository.save(profile);

		return createSettingsEditView(user, messageId);
	}
    
	public DeleteMessage closeSettings(User user, Integer messageId) {
        sessionService.setState(user, UserState.DEFAULT);
        return DeleteMessage.builder()
                .chatId(user.getTelegramUserId())
                .messageId(messageId)
                .build();
    }

	private SendMessage createSettingsView(User user) {
		UserProfile profile = getProfileOrThrow(user);
		boolean isConnected = userService.isStravaConnected(user.getTelegramUserId());
		return viewService.getSettingsMessage(user.getTelegramUserId(), user, profile, isConnected);
	}

	private EditMessageText createSettingsEditView(User user, Integer messageId) {
		UserProfile profile = getProfileOrThrow(user);
		boolean isConnected = userService.isStravaConnected(user.getTelegramUserId());
		return viewService.getSettingsEditMessage(user.getTelegramUserId(), messageId, user, profile, isConnected);
	}

	private UserProfile getProfileOrThrow(User user) {
		return userProfileRepository.findByUser(user)
				.orElseThrow(() -> new IllegalStateException("UserProfile not found for user " + user.getId()));
	}
}