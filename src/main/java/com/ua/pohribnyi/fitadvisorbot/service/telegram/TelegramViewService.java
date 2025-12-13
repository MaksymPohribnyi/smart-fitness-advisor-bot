package com.ua.pohribnyi.fitadvisorbot.service.telegram;

import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;

import com.ua.pohribnyi.fitadvisorbot.model.dto.analytics.PeriodReportDto;
import com.ua.pohribnyi.fitadvisorbot.model.dto.analytics.PeriodReportDto.MetricResult;
import com.ua.pohribnyi.fitadvisorbot.model.dto.google.DailyAdviceResponse;
import com.ua.pohribnyi.fitadvisorbot.model.entity.user.User;
import com.ua.pohribnyi.fitadvisorbot.model.entity.user.UserProfile;
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
	private static final String SPECIAL_CHARS = "_*[]()~`>#+-=|{}.!\\";
	private static final String ALLOWED_MARKDOWN_CHARS = "*_`";

	/**
	 * Message for existing users (returning to the bot). Sends a welcome message
	 * and the main ReplyKeyboard menu.
	 */
	public SendMessage getWelcomeBackMessage(Long chatId, String firstName) {
		String lang = messageService.getLangCode(chatId);
		String name = firstName != null ? firstName : messageService.getMessage("format.friend", lang);

		String text = escapeMarkdownV2(messageService.getMessage("bot.welcome.already_connected", lang, name));

		return messageBuilder.createMessageWithKeyboard(chatId, text, keyboardBuilder.createMainMenuKeyboard(lang));
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
				keyboardBuilder.createLevelSelectionKeyboard(lang, "onboarding", false));
	}

	/**
	 * Sends the second onboarding question (Profile Goal).
	 */
	public EditMessageText getOnboardingGoalQuestion(Long chatId, Integer messageId) {
		String lang = messageService.getLangCode(chatId);
		String text = escapeMarkdownV2(messageService.getMessage("onboarding.goal.question", lang));

		return messageBuilder.createEditMessageWithKeyboard(chatId, messageId, text,
				keyboardBuilder.createGoalSelectionKeyboard(lang, "onboarding", false));
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


	public EditMessageText getOnboardingAgeQuestion(Long chatId, Integer messageId) {
		String lang = messageService.getLangCode(chatId);
		String text = escapeMarkdownV2(messageService.getMessage("onboarding.age.question", lang));
		return messageBuilder.createEditMessageWithKeyboard(chatId, messageId, text,
				keyboardBuilder.createAgeSelectionKeyboard(lang));
	}

	public SendMessage getAnalyticsReportMessage(Long chatId, PeriodReportDto report) {
		String lang = messageService.getLangCode(chatId);
		String text = buildAnalyticsReportText(chatId, report, lang, false);

		return messageBuilder.createMessageWithKeyboard(chatId, text,
				keyboardBuilder.createAnalyticsKeyboard(lang, false, report.getPeriodKey()));
	}

	public EditMessageText getAnalyticsReportEditMessage(Long chatId, Integer messageId, PeriodReportDto report,
			boolean showDetails) {
		String lang = messageService.getLangCode(chatId);
		String text = buildAnalyticsReportText(chatId, report, lang, showDetails);

		return messageBuilder.createEditMessageWithKeyboard(chatId, messageId, text,
				keyboardBuilder.createAnalyticsKeyboard(lang, showDetails, report.getPeriodKey()));
    }
	
	public SendMessage getDiaryStartMessage(Long chatId, boolean isManual) {
        String lang = messageService.getLangCode(chatId);
		String textKey = isManual ? "diary.question.sleep_manual" : "diary.question.sleep";
		String text = escapeMarkdownV2(messageService.getMessage(textKey, lang));
		return messageBuilder.createMessageWithKeyboard(chatId, text, 
				keyboardBuilder.createSleepRatingKeyboard(lang));
    }

    public EditMessageText getDiaryStressQuestion(Long chatId, Integer messageId) {
        String lang = messageService.getLangCode(chatId);
        String text = escapeMarkdownV2(messageService.getMessage("diary.question.stress", lang));
        return messageBuilder.createEditMessageWithKeyboard(chatId, messageId, text, 
                keyboardBuilder.createStressRatingKeyboard(lang));
    }

    public EditMessageText getDiaryActivityConfirmationQuestion(Long chatId, Integer messageId) {
        String lang = messageService.getLangCode(chatId);
        String text = escapeMarkdownV2(messageService.getMessage("diary.question.activity_check", lang));
        return messageBuilder.createEditMessageWithKeyboard(chatId, messageId, text, 
                keyboardBuilder.createActivityConfirmationKeyboard(lang));
    }

    public EditMessageText getDiaryActivityTypeQuestion(Long chatId, Integer messageId) {
        String lang = messageService.getLangCode(chatId);
        String text = escapeMarkdownV2(messageService.getMessage("diary.question.act_type", lang));
        return messageBuilder.createEditMessageWithKeyboard(chatId, messageId, text, 
                keyboardBuilder.createActivityTypeKeyboard(lang));
    }

    public EditMessageText getDiaryDurationQuestion(Long chatId, Integer messageId) {
        String lang = messageService.getLangCode(chatId);
        String text = escapeMarkdownV2(messageService.getMessage("diary.question.act_duration", lang));
        return messageBuilder.createEditMessageWithKeyboard(chatId, messageId, text, 
                keyboardBuilder.createDurationKeyboard(lang));
    }

    public EditMessageText getDiaryIntensityQuestion(Long chatId, Integer messageId) {
        String lang = messageService.getLangCode(chatId);
        String text = escapeMarkdownV2(messageService.getMessage("diary.question.act_intensity", lang));
        return messageBuilder.createEditMessageWithKeyboard(chatId, messageId, text, 
                keyboardBuilder.createIntensityKeyboard(lang));
    }

    public EditMessageText getDiaryWaitMessage(Long chatId, Integer messageId) {
        String lang = messageService.getLangCode(chatId);
        String text = escapeMarkdownV2(messageService.getMessage("diary.generating.advice", lang));
        return messageBuilder.createEditMessage(chatId, messageId, text); 
    }
    
    public SendMessage getDiaryAdviceMessage(Long chatId, DailyAdviceResponse advice) {
        String lang = messageService.getLangCode(chatId);
    	
		String text = escapeMarkdownV2(messageService.getMessage("diary.generating.template", lang,
				advice.getAnalysis(), advice.getStatus(), advice.getAdvice()));
		return messageBuilder.createMessage(chatId, text);
	}

    public EditMessageText recoverFromMissingDraft(User user, Integer messageId) {
        // Returns a "Session expired" message update
        String lang = messageService.getLangCode(user.getTelegramUserId());
        String text = escapeMarkdownV2(messageService.getMessage("diary.error.missing_draft", lang));
		return messageBuilder.createEditMessage(user.getTelegramUserId(), messageId, text);
    }

	public SendMessage getSettingsMessage(Long chatId, User user, UserProfile profile, boolean isStravaConnected) {
		String text = buildSettingsText(chatId, user, profile, isStravaConnected);
		String lang = messageService.getLangCode(chatId);
		return messageBuilder.createMessageWithKeyboard(chatId, text,
				keyboardBuilder.createSettingsKeyboard(lang, isStravaConnected));

	}
	
	/**
	 * Updates the existing message to show the settings dashboard.
	 */
	public EditMessageText getSettingsEditMessage(Long chatId, Integer messageId, User user, UserProfile profile,
			boolean isStravaConnected) {
		String text = buildSettingsText(chatId, user, profile, isStravaConnected);
		String lang = messageService.getLangCode(chatId);

		return messageBuilder.createEditMessageWithKeyboard(chatId, messageId, text,
				keyboardBuilder.createSettingsKeyboard(lang, isStravaConnected));
	}

	/**
     * Edit-version of the Goal question.
     */
    public EditMessageText getSettingsGoalEditMessage(Long chatId, Integer messageId) {
        String lang = messageService.getLangCode(chatId);
        String text = escapeMarkdownV2(messageService.getMessage("settings.edit.choose_goal", lang));
        return messageBuilder.createEditMessageWithKeyboard(chatId, messageId, text,
                keyboardBuilder.createGoalSelectionKeyboard(lang, "settings", true));
    }

    /**
     * Edit-version of the Level question.
     */
    public EditMessageText getSettingsLevelEditMessage(Long chatId, Integer messageId) {
        String lang = messageService.getLangCode(chatId);
        String text = escapeMarkdownV2(messageService.getMessage("settings.edit.choose_level", lang));
        return messageBuilder.createEditMessageWithKeyboard(chatId, messageId, text,
                keyboardBuilder.createLevelSelectionKeyboard(lang, "settings", true));
    }

    /**
     * Edit-version of the Age question.
     */
    public EditMessageText getOnboardingAgeQuestionEdit(Long chatId, Integer messageId) {
        String lang = messageService.getLangCode(chatId);
        String text = escapeMarkdownV2(messageService.getMessage("onboarding.age.question", lang));
        return messageBuilder.createEditMessageWithKeyboard(chatId, messageId, text,
                keyboardBuilder.createAgeSelectionKeyboard(lang));
    }
    
    /**
     * Shows Strava connect link by editing the message.
     */
    public EditMessageText getStravaConnectMessageEdit(Long chatId, Integer messageId, String authUrl) {
        String lang = messageService.getLangCode(chatId);
        String text = escapeMarkdownV2(messageService.getMessage("strava.connect_message", lang));
        
        return messageBuilder.createEditMessageWithKeyboard(chatId, messageId, text,
                keyboardBuilder.createUrlButtonKeyboard(messageService.getMessage("button.authorize_strava", lang), authUrl));
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

    private String buildSettingsText(Long chatId, User user, UserProfile profile, boolean isStravaConnected) {
    	String lang = messageService.getLangCode(chatId);

		// Safe access to profile fields with fallback
		String goalText = profile.getGoal() != null
				? messageService.getMessage("onboarding.goal." + profile.getGoal().toLowerCase(), lang)
				: "N/A";

		String levelText = profile.getLevel() != null
				? messageService.getMessage("onboarding.level." + profile.getLevel().toLowerCase(), lang)
				: "N/A";

		String ageText = profile.getAge() != null ? String.valueOf(profile.getAge()) : "N/A";

		String stravaStatus = isStravaConnected ? messageService.getMessage("settings.strava_status.connected", lang)
				: messageService.getMessage("settings.strava_status.disconnected", lang);

		return escapeMarkdownV2(messageService.getMessage("settings.header", lang, user.getFirstName(), ageText,
				levelText, goalText, stravaStatus));
	}
    
	private String buildAnalyticsReportText(Long chatId, PeriodReportDto report, String lang, boolean showDetails) {
		StringBuilder sb = new StringBuilder();

		String goalTitle = messageService.getMessage(report.getGoalName(), lang);
		String consistencyVerdict = messageService.getMessage(report.getConsistencyVerdictKey(), lang);
		String periodName = messageService.getMessage(report.getPeriodKey(), lang);
		
		// Header
		sb.append(escapeMarkdownV2(messageService.getMessage("analytics.report.header", lang, periodName))).append("\n\n");

		// Summary section
		sb.append(escapeMarkdownV2(messageService.getMessage("analytics.report.summary_section", lang, goalTitle, consistencyVerdict,
				report.getConsistencyScore()))).append("\n\n");
		
		// Stats
		sb.append(escapeMarkdownV2(messageService.getMessage("analytics.report.base_stats_section", lang, report.getTotalActivities(),
				String.format(Locale.US, "%.1f", report.getTotalDistanceKm()),
				String.format(Locale.US, "%.1f", report.getTotalDurationHours())))).append("\n\n");

		// Base Metrics (Foundation)
		sb.append(escapeMarkdownV2(messageService.getMessage("analytics.report.base_metrics_header", lang))).append("\n");
		appendMetricsList(sb, report.getBaseMetrics(), lang, showDetails);
		sb.append("\n");

		// Smart Insights
		sb.append(escapeMarkdownV2(messageService.getMessage("analytics.report.insights_header", lang))).append("\n");
		appendMetricsList(sb, report.getAdvancedMetrics(), lang, showDetails);
		sb.append("\n");
		
		// 6. Prediction 
        if (report.getPredictionMetric() != null) {
        	sb.append(escapeMarkdownV2(messageService.getMessage("analytics.report.predict_header", lang))).append("\n");
        	appendMetricsList(sb, List.of(report.getPredictionMetric()), lang, showDetails);
            sb.append("\n");
        }
		
		// Advisor Summary (Behavioral Frame)
        String advisorText = messageService.getMessage(report.getAdvisorSummaryKey(), lang);
        String expertBlock = escapeMarkdownV2(
                messageService.getMessage("analytics.report.advisor_section", lang, advisorText));
        sb.append(expertBlock).append("\n");
        
		// Footer
		sb.append(escapeMarkdownV2(messageService.getMessage("analytics.report.footer", lang)));

		return sb.toString();
		
	}
	
	private void appendMetricsList(StringBuilder sb, List<MetricResult> metrics, String lang, boolean showDetails) {
		for (MetricResult metric : metrics) {
			String titleKey = "analytics.metric.title." + metric.getType().name();

			String title = messageService.getMessage(titleKey, lang);

			// {0}=Title, {1}=Value, {2}=Emoji
			String line = escapeMarkdownV2(
					messageService.getMessage("analytics.report.metric_line", lang, title, metric.getFormattedValue()));
			sb.append(line).append("\n");

			if (showDetails) {
				String descKey = "analytics.metric.desc." + metric.getType().name();
				String desc = messageService.getMessage(descKey, lang);
				sb.append(escapeMarkdownV2("_" + desc + "_")).append("\n");
			}
			sb.append("\n");
		}
	}

}