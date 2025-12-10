package com.ua.pohribnyi.fitadvisorbot.service.analytics.diary;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;

import com.ua.pohribnyi.fitadvisorbot.enums.UserState;
import com.ua.pohribnyi.fitadvisorbot.model.dto.analytics.DiaryDraft;
import com.ua.pohribnyi.fitadvisorbot.model.entity.Activity;
import com.ua.pohribnyi.fitadvisorbot.model.entity.DailyMetric;
import com.ua.pohribnyi.fitadvisorbot.model.entity.user.User;
import com.ua.pohribnyi.fitadvisorbot.model.entity.user.UserProfile;
import com.ua.pohribnyi.fitadvisorbot.repository.data.ActivityRepository;
import com.ua.pohribnyi.fitadvisorbot.repository.data.DailyMetricRepository;
import com.ua.pohribnyi.fitadvisorbot.repository.user.UserProfileRepository;
import com.ua.pohribnyi.fitadvisorbot.service.ai.GeminiApiClient;
import com.ua.pohribnyi.fitadvisorbot.service.ai.prompt.GeminiPromptBuilderService;
import com.ua.pohribnyi.fitadvisorbot.service.analytics.UserPhysiologyService;
import com.ua.pohribnyi.fitadvisorbot.service.telegram.FitnessAdvisorBotService;
import com.ua.pohribnyi.fitadvisorbot.service.telegram.TelegramViewService;
import com.ua.pohribnyi.fitadvisorbot.service.user.UserSessionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class DiaryService {

	private final UserSessionService sessionService;
	private final DailyMetricRepository metricRepository;
	private final ActivityRepository activityRepository;
	private final UserProfileRepository profileRepository;

	private final DiaryStateService stateService;
	private final UserPhysiologyService userPhysiologyService;

	private final TelegramViewService viewService;
	private final GeminiApiClient geminiClient;
	private final GeminiPromptBuilderService promptBuilder;

	/**
	 * Starts the daily check-in flow. Returns SendMessage to be executed by
	 * CommandHandler or Scheduler.
	 */
	public SendMessage startDailyCheckIn(User user) {
		stateService.getAndRemove(user.getId());
		
		sessionService.setState(user, UserState.AWAITING_SLEEP);
		return viewService.getDiaryStartMessage(user.getTelegramUserId());
	}

	public EditMessageText processSleepInput(User user, String data, Integer messageId) {
		double hours = parseSleepData(data);
		stateService.updateSleep(user.getId(), hours);

		sessionService.setState(user, UserState.AWAITING_STRESS);
		return viewService.getDiaryStressQuestion(user.getTelegramUserId(), messageId);
	}

	public EditMessageText processStressInput(User user, String data, Integer messageId) {
		int stress = Integer.parseInt(data.split(":")[2]);
		stateService.updateStress(user.getId(), stress);

		sessionService.setState(user, UserState.AWAITING_ACTIVITY_CONFIRMATION);
		return viewService.getDiaryActivityConfirmationQuestion(user.getTelegramUserId(), messageId);
	}

	/**
	 * Returns EditMessageText. If flow ends here (no activity), logic triggers
	 * Async Advice generation.
	 */
	public EditMessageText processActivityConfirmation(User user, String data, Integer messageId,
			FitnessAdvisorBotService bot) {
		boolean hadActivity = data.endsWith("yes");
		Long chatId = user.getTelegramUserId();

		stateService.updateActivityPresence(user.getId(), hadActivity);
		
		if (!hadActivity) {
			return finishCheckIn(user, messageId, bot, false);
		} else {
			sessionService.setState(user, UserState.AWAITING_ACTIVITY_TYPE);
			return viewService.getDiaryActivityTypeQuestion(chatId, messageId);
		}
	}

	public EditMessageText processActivityType(User user, String data, Integer messageId) {
		String type = data.split(":")[2];
		stateService.updateActivityType(user.getId(), type);

		sessionService.setState(user, UserState.AWAITING_ACTIVITY_DURATION);
		return viewService.getDiaryDurationQuestion(user.getTelegramUserId(), messageId);
	}

	public EditMessageText processActivityDuration(User user, String data, Integer messageId) {
		int minutes = Integer.parseInt(data.split(":")[2]);
		stateService.updateDuration(user.getId(), minutes);

		sessionService.setState(user, UserState.AWAITING_ACTIVITY_INTENSITY);
		return viewService.getDiaryIntensityQuestion(user.getTelegramUserId(), messageId);
	}

	@Transactional
	public EditMessageText processActivityIntensity(User user, String data, Integer messageId,
			FitnessAdvisorBotService bot) {
		int rpe = Integer.parseInt(data.split(":")[2]);
		stateService.updateRpe(user.getId(), rpe);

		return finishCheckIn(user, messageId, bot, true);
	}

	/**
	 * Finalizes the flow: 1. Resets state. 2. Returns "Thinking..." message to UI.
	 * 3. Triggers Async AI Advice generation.
	 */
	private EditMessageText finishCheckIn(User user, Integer messageId, FitnessAdvisorBotService bot,
			boolean hadActivity) {
		// 1. Retrieve full draft
		DiaryDraft draft = stateService.getAndRemove(user.getId());
		if (draft == null) {
			// Edge case: expired cache or restart
			sessionService.setState(user, UserState.DEFAULT);
			return viewService.recoverFromMissingDraft(user, messageId);
		}

		// 2. Persist to DB (Atomic)
		persistData(user, draft);

		// 3. UI & AI
		sessionService.setState(user, UserState.DEFAULT);

		// Trigger Async AI Generation
		generateAndSendAdviceAsync(user, draft, bot);

		return viewService.getDiaryWaitMessage(user.getTelegramUserId(), messageId);
	}

	@Transactional
	protected void persistData(User user, DiaryDraft draft) {
		LocalDate today = LocalDate.now();
		
		// 1. Save Metrics
		DailyMetric metric = metricRepository.findMetricsByUserAndDateAfter(user, today).stream()
				.filter(m -> m.getDate().equals(today))
				.findFirst()
				.orElse(new DailyMetric());

		metric.setUser(user);
		metric.setDate(today);
		metric.setSynthetic(false);
		
		if (draft.getSleepHours() != null)
			metric.setSleepHours(draft.getSleepHours());
		if (draft.getStressLevel() != null)
			metric.setStressLevel(draft.getStressLevel());
		
		// SIMPLE STEP LOGIC: 5500 base + 1500 if active
        int totalSteps = UserPhysiologyService.BASE_DAILY_STEPS;
        if (draft.isHadActivity()) {
            totalSteps += UserPhysiologyService.ACTIVITY_BONUS_STEPS;
        }
        metric.setDailyBaseSteps(totalSteps);
		
		metricRepository.save(metric);

		// 2. Save Activity if present
		if (draft.isHadActivity()) {
			Activity activity = new Activity();
			activity.setUser(user);
			activity.setSynthetic(false);

			// Logic: Morning input -> Yesterday evening. Evening input -> Today evening.
			LocalDateTime activityDate = LocalDateTime.now().getHour() < 12
					? LocalDateTime.now().minusDays(1).withHour(19)
					: LocalDateTime.now().withHour(18);
			activity.setDateTime(activityDate);

			activity.setType(draft.getActivityType());
			activity.setDurationSeconds(draft.getDurationMinutes() * 60);

			// Calc Pulse from RPE
			int rpe = draft.getRpe() != null ? draft.getRpe() : 5;
			int estimatedPulse = 90 + (rpe * 9);
			activity.setAvgPulse(estimatedPulse);
			activity.setMaxPulse(estimatedPulse + 20);

			// Calc Calories (approx)
			activity.setCaloriesBurned(draft.getDurationMinutes() * (rpe + 2));

			// Calc Steps (NEW Feature)
			int steps = userPhysiologyService.calculateSteps(draft.getActivityType(), draft.getDurationMinutes(), rpe);
			activity.setActivitySteps(steps);
			// Rough distance approx
			activity.setDistanceMeters((int) (steps * 0.75));

			activityRepository.save(activity);
		}
	}
	
	@Async("aiGenerationExecutor") // Use existing pool
	public void generateAndSendAdviceAsync(User user, DiaryDraft draft, FitnessAdvisorBotService bot) {
			UserProfile profile = profileRepository.findByUser(user)
					.orElseThrow(() -> new IllegalStateException("UserProfile missing for user " + user.getId()));

			DailyMetric todayMetric = new DailyMetric(); 
			todayMetric.setSleepHours(draft.getSleepHours());
			todayMetric.setStressLevel(draft.getStressLevel());
			
			List<Activity> recentActivities = activityRepository.findActivitiesByUserAndDateAfter(user,
					LocalDateTime.now().minusDays(7));

			String prompt = promptBuilder.buildDailyAdvicePrompt(profile, recentActivities, todayMetric, draft.isHadActivity());
			log.debug("Generated AI prompt for daily advice for user {}: {}", user.getId(), prompt);
			String advice = geminiClient.generateText(prompt); // Ensure generateText exists in Client
			
			SendMessage adviceMsg = viewService.getDiaryAdviceMessage(user.getTelegramUserId(), advice);
			bot.sendMessage(adviceMsg);
	}

	// --- Helpers ---

	private double parseSleepData(String data) {
		if (data.endsWith("bad"))
			return 5.0;
		if (data.endsWith("avg"))
			return 6.5;
		if (data.endsWith("good"))
			return 7.5;
		return 7.0;
	}

}