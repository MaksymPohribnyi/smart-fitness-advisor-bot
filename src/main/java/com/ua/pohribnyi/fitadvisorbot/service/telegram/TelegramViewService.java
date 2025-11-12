package com.ua.pohribnyi.fitadvisorbot.service.telegram;

import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

import com.ua.pohribnyi.fitadvisorbot.model.dto.strava.StravaActivityDto;
import com.ua.pohribnyi.fitadvisorbot.model.dto.strava.StravaAthleteDto;
import com.ua.pohribnyi.fitadvisorbot.util.KeyboardBuilderService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class TelegramViewService {

	private final MessageService messageService;
	private final MessageBuilderService messageBuilder;
	private final KeyboardBuilderService keyboardBuilder;
	private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
	private static final String SPECIAL_CHARS = "_*[]()~`>#+-=|{}.!\\";
    private static final String ALLOWED_MARKDOWN_CHARS = "*_~`";

	/**
	 * Message for existing users (returning to the bot). Sends a welcome message
	 * and the main ReplyKeyboard menu.
	 */
	public SendMessage getWelcomeBackMessage(Long chatId, String firstName) {
		String lang = messageService.getLangCode(chatId);
		String name = firstName != null ? firstName : messageService.getMessage("format.friend", lang);

		String text = escapeMarkdownV2(messageService.getMessage("bot.welcome.already_connected", lang, name));

		return messageBuilder.createMessageWithKeyboard(chatId, text,
				keyboardBuilder.createMainMenuKeyboard(lang));
	}

	/**
	 * Message for brand new users (starts the onboarding flow).
	 */
	public SendMessage getOnboardingStartMessage(Long chatId, String firstName) {
		String lang = messageService.getLangCode(chatId);
		String name = firstName != null ? firstName : messageService.getMessage("format.friend", lang);

		String text = escapeMarkdownV2(messageService.getMessage("bot.welcome", lang, name));

		return messageBuilder.createMessage(chatId, text);
	}

	/**
	 * Sends the first onboarding question (Profile Level).
	 */
	public SendMessage getOnboardingLevelQuestion(Long chatId) {
		String lang = messageService.getLangCode(chatId);
		String text = escapeMarkdownV2(messageService.getMessage("onboarding.level.question", lang));

		return messageBuilder.createMessageWithKeyboard(chatId, text,
				keyboardBuilder.createLevelSelectionKeyboard(lang));
	}

	/**
	 * Sends the second onboarding question (Profile Goal).
	 */
	public EditMessageText getOnboardingGoalQuestion(Long chatId, Integer messageId) {
		String lang = messageService.getLangCode(chatId);
		String text = escapeMarkdownV2(messageService.getMessage("onboarding.goal.question", lang));

		return messageBuilder.createEditMessageWithKeyboard(chatId, messageId, text,
				keyboardBuilder.createGoalSelectionKeyboard(lang));
	}

	/**
	 * Final message after onboarding is complete.
	 */
	public SendMessage getOnboardingCompletedMessage(Long chatId) {
		String lang = messageService.getLangCode(chatId);
		String text = escapeMarkdownV2(messageService.getMessage("onboarding.completed", lang));

		return messageBuilder.createMessageWithKeyboard(chatId, text, keyboardBuilder.createMainMenuKeyboard(lang));
	}

	public SendMessage getGenerationWaitMessage(Long chatId) {
		String lang = messageService.getLangCode(chatId);
		String text = escapeMarkdownV2(messageService.getMessage("onboarding.job.started", lang));
		return messageBuilder.createMessage(chatId, text);
	}
	
	
	/**
	 * General error message.
	 */
	public SendMessage getGeneralErrorMessage(Long chatId) {
		String lang = messageService.getLangCode(chatId);
		String text = escapeMarkdownV2(messageService.getMessage("error.unknown", lang));

		return messageBuilder.createMessage(chatId, text);
	}

	/**
	 * Strava not connected error.
	 */
	public SendMessage getErrorStravaNotConnected(Long chatId) {
		String lang = messageService.getLangCode(chatId);
		String text = escapeMarkdownV2(messageService.getMessage("error.strava.not_connected", lang));

		return messageBuilder.createMessage(chatId, text);
	}

	public SendMessage getAthleteInfoMessage(Long chatId, StravaAthleteDto athlete) {
		String lang = messageService.getLangCode(chatId);

		String firstName = athlete.firstName() != null ? athlete.firstName() : "";
		String lastName = athlete.lastName() != null ? athlete.lastName() : "";
		String location = formatLocation(athlete.city(), athlete.state(), athlete.country(), lang);
		String gender = formatGender(athlete.sex(), lang);
		String weight = formatWeight(athlete.weight(), lang);
		String followers = athlete.followerCount() != null ? String.valueOf(athlete.followerCount()) : "0";
		String friends = athlete.friendCount() != null ? String.valueOf(athlete.friendCount()) : "0";
		String premiumStatus = formatPremiumStatus(athlete.premium(), lang);

		String joinedDate;
		try {
			joinedDate = java.time.OffsetDateTime.parse(athlete.createdAt()).toLocalDate()
					.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
		} catch (Exception e) {
			log.warn("Could not parse athlete createdAt date: {}", athlete.createdAt());
			joinedDate = athlete.createdAt();
		}

		String unsafeText = messageService.getMessage("athlete.info", lang, firstName, // {0}
				lastName, // {1}
				location, // {2}
				gender, // {3}
				weight, // {4}
				followers, // {5}
				friends, // {6}
				premiumStatus, // {7}
				joinedDate // {8}
		);

		String text = escapeMarkdownV2(unsafeText);

		return SendMessage.builder().chatId(chatId).text(text).parseMode("MarkdownV2").build();
	}

	public SendMessage getActivitiesMessage(Long chatId, List<StravaActivityDto> activities) {
		String lang = messageService.getLangCode(chatId);
		String text = formatActivitiesList(activities, lang);

		return SendMessage.builder().chatId(chatId).text(text).parseMode("MarkdownV2").build();
	}

	private String formatActivitiesList(List<StravaActivityDto> activities, String lang) {
		if (activities == null || activities.isEmpty()) {
			String unsafeText = messageService.getMessage("athlete.activity.no_activities", lang);
			return escapeMarkdownV2(unsafeText);
		}
		String unsafeText = messageService.getMessage("athlete.activity.header", lang);
		String text = escapeMarkdownV2(unsafeText);
		StringBuilder sb = new StringBuilder(text);

		for (int i = 0; i < activities.size(); i++) {
			StravaActivityDto activity = activities.get(i);

			String avgSpeedKmH = "N/A";
			if (activity.averageSpeed() != null) {
				avgSpeedKmH = String.format("%.2f", activity.averageSpeed() * 3.6);
			}

			String unsafeActivityItem = messageService.getMessage("athlete.activity.item", lang, (i + 1),
					activity.name(), activity.type(), activity.startDateLocal().format(DATE_FORMATTER),
					String.format("%.2f", activity.distance() / 1000.0), formatDuration(activity.movingTime()),
					avgSpeedKmH, formatHeartRate(activity.averageHeartrate(), lang),
					formatCalories(activity.calories(), lang), String.format("%.0f", activity.elevationGain()));

			String activityItem = escapeMarkdownV2(unsafeActivityItem);
			sb.append(activityItem);
		}
		return sb.toString();
	}

	private String formatLocation(String city, String state, String country, String lang) {
		StringBuilder location = new StringBuilder();
		if (city != null && !city.isBlank()) {
			location.append(city);
		}
		if (state != null && !state.isBlank()) {
			if (!location.isEmpty())
				location.append(", ");
			location.append(state);
		}
		if (country != null && !country.isBlank()) {
			if (!location.isEmpty())
				location.append(", ");
			location.append(country);
		}
		if (location.isEmpty()) {
			return messageService.getMessage("format.location.unknown", lang);
		}
		return location.toString();
	}

	private String formatGender(String sex, String lang) {
		String key = switch (sex != null ? sex.toUpperCase() : "") {
		case "M" -> "format.gender.m";
		case "F" -> "format.gender.f";
		default -> "format.gender.unknown";
		};
		return messageService.getMessage(key, lang);
	}

	private String formatWeight(Float weight, String lang) {
		if (weight == null)
			return messageService.getMessage("format.na", lang);
		return messageService.getMessage("format.weight.kg", lang, String.format("%.1f", weight));
	}

	private String formatPremiumStatus(Boolean premium, String lang) {
		String key = Boolean.TRUE.equals(premium) ? "format.premium.yes" : "format.premium.no";
		return messageService.getMessage(key, lang);
	}

	private String formatDuration(int seconds) {
		int hours = seconds / 3600;
		int minutes = (seconds % 3600) / 60;
		if (hours > 0) {
			return String.format("%dг %dхв", hours, minutes);
		}
		return String.format("%dхв", minutes);
	}

	private String formatHeartRate(Float heartRate, String lang) {
		if (heartRate == null)
			return messageService.getMessage("format.na", lang);
		return messageService.getMessage("format.heartrate.bpm", lang, String.format("%.0f", heartRate));
	}

	private String formatCalories(Integer calories, String lang) {
		if (calories == null)
			return messageService.getMessage("format.na", lang);
		return messageService.getMessage("format.calories.kcal", lang, calories);
	}

	public static String escapeMarkdownV2(String text) {
		if (text == null || text.isEmpty()) {
			return text;
		}
		StringBuilder result = new StringBuilder(text.length() * 2);
		for (char c : text.toCharArray()) {
			if (SPECIAL_CHARS.indexOf(c) >= 0 && ALLOWED_MARKDOWN_CHARS.indexOf(c) < 0) {
				result.append('\\');
			}
			result.append(c);
		}
		return result.toString();
	}
}