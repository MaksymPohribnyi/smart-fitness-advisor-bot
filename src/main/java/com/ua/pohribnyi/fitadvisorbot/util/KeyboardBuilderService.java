package com.ua.pohribnyi.fitadvisorbot.util;

import java.util.List;

import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

import com.ua.pohribnyi.fitadvisorbot.service.telegram.MessageService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class KeyboardBuilderService {

	private final MessageService messageService;

	/**
	 * Creates global ReplyKeyboardMarkup (buttons upside). 
	 * 
	 * @param lang User language code
	 * @return Already built keyboard
	 */
	public ReplyKeyboardMarkup createMainMenuKeyboard(String lang) {
			return ReplyKeyboardMarkup.builder()
					.keyboard(List.of(
							new KeyboardRow(List.of(
									new KeyboardButton(messageService.getMessage("menu.diary", lang)),
									new KeyboardButton(messageService.getMessage("menu.analytics", lang)))),
							new KeyboardRow(List.of(
									new KeyboardButton(messageService.getMessage("menu.settings", lang))))))
					.resizeKeyboard(true)
					.oneTimeKeyboard(false)
					.build();
	}

	/**
	 * Creates onboarding level selection keyboard.
	 */
	public InlineKeyboardMarkup createLevelSelectionKeyboard(String lang) {
		return InlineKeyboardMarkup.builder()
				.keyboardRow(List.of(InlineKeyboardButton.builder()
						.text(messageService.getMessage("onboarding.level.beginner", lang))
						.callbackData("onboarding:level:beginner").build()))
				.keyboardRow(List.of(InlineKeyboardButton.builder()
						.text(messageService.getMessage("onboarding.level.moderate", lang))
						.callbackData("onboarding:level:moderate").build()))
				.keyboardRow(List
						.of(InlineKeyboardButton.builder().text(messageService.getMessage("onboarding.level.pro", lang))
								.callbackData("onboarding:level:pro").build()))
				.build();
	}

	/**
	 * Creates onboarding goal selection keyboard.
	 */
	public InlineKeyboardMarkup createGoalSelectionKeyboard(String lang) {
		return InlineKeyboardMarkup.builder()
				.keyboardRow(List.of(InlineKeyboardButton.builder()
						.text(messageService.getMessage("onboarding.goal.lose_weight", lang))
						.callbackData("onboarding:goal:lose_weight").build()))
				.keyboardRow(List.of(
						InlineKeyboardButton.builder().text(messageService.getMessage("onboarding.goal.run_10k", lang))
								.callbackData("onboarding:goal:run_10k").build()))
				.keyboardRow(List.of(InlineKeyboardButton.builder()
						.text(messageService.getMessage("onboarding.goal.build_muscle", lang))
						.callbackData("onboarding:goal:build_muscle").build()))
				.keyboardRow(List.of(
						InlineKeyboardButton.builder().text(messageService.getMessage("onboarding.goal.health", lang))
								.callbackData("onboarding:goal:health").build()))
				.build();
	}

	/**
	 * Creates a single URL button keyboard.
	 */
	public InlineKeyboardMarkup createUrlButtonKeyboard(String buttonText, String url) {
		return InlineKeyboardMarkup.builder()
				.keyboardRow(List.of(InlineKeyboardButton.builder().text(buttonText).url(url).build())).build();
	}

	public InlineKeyboardMarkup createJobRetryKeyboard(String lang, Long jobId) {
		return InlineKeyboardMarkup.builder()
				.keyboardRow(List
						.of(InlineKeyboardButton.builder().text(messageService.getMessage("onboarding.job.retry", lang))
								.callbackData("job:retry:" + jobId).build()))
				.build();
	}

	public InlineKeyboardMarkup createAgeSelectionKeyboard(String lang) {
		return InlineKeyboardMarkup.builder()
				.keyboardRow(
						List.of(InlineKeyboardButton.builder().text("18-25").callbackData("onboarding:age:21").build(),
								InlineKeyboardButton.builder().text("26-35").callbackData("onboarding:age:30").build()))
				.keyboardRow(
						List.of(InlineKeyboardButton.builder().text("36-45").callbackData("onboarding:age:40").build(),
								InlineKeyboardButton.builder().text("46+").callbackData("onboarding:age:50").build()))
				.build();
	}
	
}