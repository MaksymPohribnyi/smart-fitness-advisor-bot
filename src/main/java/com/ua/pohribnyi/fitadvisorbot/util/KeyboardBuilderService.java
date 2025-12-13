package com.ua.pohribnyi.fitadvisorbot.util;

import java.util.ArrayList;
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
     * Creates a goal selection keyboard.
     * @param prefix Defines context: "onboarding" or "settings:save"
     * @param includeBackBtn Adds a "Back" button for Settings context
     */
	public InlineKeyboardMarkup createGoalSelectionKeyboard(String lang, String prefix, boolean includeBackBtn) {
		var builder = InlineKeyboardMarkup.builder();
		
		builder.keyboardRow(List.of(InlineKeyboardButton.builder()
				.text(messageService.getMessage("onboarding.goal.lose_weight", lang))
				.callbackData(prefix + ":goal:lose_weight").build()))
		.keyboardRow(List.of(InlineKeyboardButton.builder()
				.text(messageService.getMessage("onboarding.goal.run_10k", lang))
				.callbackData(prefix +  ":goal:run_10k").build()))
		.keyboardRow(List.of(InlineKeyboardButton.builder()
				.text(messageService.getMessage("onboarding.goal.build_muscle", lang))
				.callbackData(prefix +  ":goal:build_muscle").build()))
		.keyboardRow(List.of(InlineKeyboardButton.builder()
				.text(messageService.getMessage("onboarding.goal.health", lang))
				.callbackData(prefix + ":goal:health").build()));
		
		if (includeBackBtn) {
			builder.keyboardRow(List.of(InlineKeyboardButton.builder()
					.text(messageService.getMessage("settings.btn.back", lang))
					.callbackData("settings:nav:main").build()));
		}
		return builder.build();
	}
	
	/**
	 * Creates onboarding level selection keyboard.
	 */
	public InlineKeyboardMarkup createLevelSelectionKeyboard(String lang, String prefix, boolean includeBackBtn) {
		var builder = InlineKeyboardMarkup.builder();
		
		builder.keyboardRow(List.of(InlineKeyboardButton.builder()
				.text(messageService.getMessage("onboarding.level.beginner", lang))
				.callbackData(prefix + ":level:beginner").build()))
		.keyboardRow(List.of(InlineKeyboardButton.builder()
				.text(messageService.getMessage("onboarding.level.moderate", lang))
				.callbackData(prefix + ":level:moderate").build()))
		.keyboardRow(List.of(InlineKeyboardButton.builder()
				.text(messageService.getMessage("onboarding.level.pro", lang))
				.callbackData(prefix + ":level:pro").build()));
		
		if (includeBackBtn) {
			builder.keyboardRow(List.of(InlineKeyboardButton.builder()
					.text(messageService.getMessage("settings.btn.back", lang))
					.callbackData("settings:nav:main").build()));
		}

        return builder.build();
	}

	/**
	 * Creates a single URL button keyboard.
	 */
	public InlineKeyboardMarkup createUrlButtonKeyboard(String buttonText, String url) {
		return InlineKeyboardMarkup.builder()
				.keyboardRow(List.of(InlineKeyboardButton.builder().text(buttonText).url(url).build())).build();
	}

	public InlineKeyboardMarkup createAnalyticsKeyboard(String lang, boolean isExpanded, String periodKey) {
		String textKey = isExpanded ? "analytics.report.button.hide" : "analytics.report.button.explain";

		String action = isExpanded ? "collapse" : "expand";
		String callbackData = String.format("analytics:%s:%s", action, periodKey);

		return InlineKeyboardMarkup.builder().keyboardRow(List.of(InlineKeyboardButton.builder()
				.text(messageService.getMessage(textKey, lang)).callbackData(callbackData).build())).build();
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

	public InlineKeyboardMarkup createSleepRatingKeyboard(String lang) {
		return InlineKeyboardMarkup.builder()
				.keyboardRow(List.of(createBtn("diary.sleep.bad", "diary:sleep:bad", lang),
						createBtn("diary.sleep.avg", "diary:sleep:avg", lang)))
				.keyboardRow(List.of(createBtn("diary.sleep.good", "diary:sleep:good", lang))).build();
	}

	public InlineKeyboardMarkup createStressRatingKeyboard(String lang) {
		return InlineKeyboardMarkup.builder()
				.keyboardRow(List.of(createBtn("diary.stress.low", "diary:stress:1", lang),
						createBtn("diary.stress.med", "diary:stress:3", lang)))
				.keyboardRow(List.of(createBtn("diary.stress.high", "diary:stress:5", lang))).build();
	}

	public InlineKeyboardMarkup createActivityConfirmationKeyboard(String lang) {
        return InlineKeyboardMarkup.builder()
            .keyboardRow(List.of(
                createBtn("diary.activity.was", "diary:act:yes", lang),
                createBtn("diary.activity.wasnt", "diary:act:no", lang)
            ))
            .build();
    }

    public InlineKeyboardMarkup createActivityTypeKeyboard(String lang) {
        return InlineKeyboardMarkup.builder()
            .keyboardRow(List.of(
                createBtn("diary.type.run", "diary:type:Run", lang),
                createBtn("diary.type.gym", "diary:type:Workout", lang)
            ))
            .keyboardRow(List.of(
                createBtn("diary.type.walk", "diary:type:Walk", lang),
                createBtn("diary.type.ride", "diary:type:Ride", lang)
            ))
            .build();
    }

    public InlineKeyboardMarkup createDurationKeyboard(String lang) {
        return InlineKeyboardMarkup.builder()
            .keyboardRow(List.of(
                InlineKeyboardButton.builder().text("20 min").callbackData("diary:dur:20").build(),
                InlineKeyboardButton.builder().text("40 min").callbackData("diary:dur:40").build(),
                InlineKeyboardButton.builder().text("60 min").callbackData("diary:dur:60").build()
            ))
            .keyboardRow(List.of(
                InlineKeyboardButton.builder().text("90+ min").callbackData("diary:dur:90").build()
            ))
            .build();
    }

    public InlineKeyboardMarkup createIntensityKeyboard(String lang) {
        return InlineKeyboardMarkup.builder()
            .keyboardRow(List.of(
                createBtn("diary.rpe.easy", "diary:rpe:3", lang),   // 1-3
                createBtn("diary.rpe.med", "diary:rpe:5", lang)     // 4-6
            ))
            .keyboardRow(List.of(
                createBtn("diary.rpe.hard", "diary:rpe:8", lang),   // 7-8
                createBtn("diary.rpe.max", "diary:rpe:10", lang)    // 9-10
            ))
            .build();
    }

	public InlineKeyboardMarkup createSettingsKeyboard(String lang, boolean isStravaConnected) {
		String stravaKey = isStravaConnected ? "settings.btn.strava_disconnect" : "settings.btn.strava_connect";
		
		return InlineKeyboardMarkup.builder()
				.keyboardRow(List.of(
						createBtn("settings.btn.edit_goal", "settings:nav:goal", lang),
						createBtn("settings.btn.edit_level", "settings:nav:level", lang)))
				.keyboardRow(List.of(createBtn(stravaKey, "settings:strava:toggle", lang)))
				.keyboardRow(List.of(createBtn("settings.btn.close", "settings:control:close", lang)))
				.build();
	}
    
    private InlineKeyboardButton createBtn(String key, String data, String lang) {
        return InlineKeyboardButton.builder()
            .text(messageService.getMessage(key, lang))
            .callbackData(data)
            .build();
    }
	
}