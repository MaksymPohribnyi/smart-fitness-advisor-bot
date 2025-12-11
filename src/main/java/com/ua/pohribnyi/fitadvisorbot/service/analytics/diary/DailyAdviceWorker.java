package com.ua.pohribnyi.fitadvisorbot.service.analytics.diary;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ua.pohribnyi.fitadvisorbot.model.dto.google.DailyAdviceResponse;
import com.ua.pohribnyi.fitadvisorbot.model.entity.Activity;
import com.ua.pohribnyi.fitadvisorbot.model.entity.DailyAdviceJob;
import com.ua.pohribnyi.fitadvisorbot.model.entity.DailyAdviceJob.Status;
import com.ua.pohribnyi.fitadvisorbot.model.entity.DailyMetric;
import com.ua.pohribnyi.fitadvisorbot.model.entity.user.User;
import com.ua.pohribnyi.fitadvisorbot.model.entity.user.UserProfile;
import com.ua.pohribnyi.fitadvisorbot.repository.data.ActivityRepository;
import com.ua.pohribnyi.fitadvisorbot.repository.data.DailyMetricRepository;
import com.ua.pohribnyi.fitadvisorbot.repository.diary.DailyAdviceJobRepository;
import com.ua.pohribnyi.fitadvisorbot.repository.user.UserProfileRepository;
import com.ua.pohribnyi.fitadvisorbot.service.ai.GeminiApiClient;
import com.ua.pohribnyi.fitadvisorbot.service.ai.prompt.GeminiPromptBuilderService;
import com.ua.pohribnyi.fitadvisorbot.service.analytics.UserPhysiologyService;
import com.ua.pohribnyi.fitadvisorbot.service.telegram.FitnessAdvisorBotService;
import com.ua.pohribnyi.fitadvisorbot.service.telegram.TelegramViewService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class DailyAdviceWorker {

	private final DailyAdviceJobRepository jobRepository;
	private final ActivityRepository activityRepository;
	private final DailyMetricRepository metricRepository;
	private final UserProfileRepository profileRepository;

	private final GeminiApiClient geminiClient;
	private final GeminiPromptBuilderService promptBuilder;
	private final TelegramViewService viewService;
	private final FitnessAdvisorBotService botService;
	private final UserPhysiologyService userPhysiologyService;
	private final ObjectMapper objectMapper;

	@Transactional
	public void processJob(Long jobId) {
		log.info("Worker started processing DailyAdviceJob {}", jobId);

		DailyAdviceJob job = jobRepository.findById(jobId)
				.orElseThrow(() -> new IllegalStateException("Job not found: " + jobId));
		User user = job.getUser();

		try {
			job.setStatus(Status.PROCESSING);
			jobRepository.save(job);

			// 1. Save Core Data
			DailyMetric metric = saveDailyMetric(job);
			
			boolean hadActivity = Boolean.TRUE.equals(job.getHadActivity());
			if (hadActivity) {
				saveActivity(job);
			}

			// 2. Generate AI Advice
			UserProfile profile = profileRepository.findByUser(user)
					.orElseThrow(() -> new IllegalStateException("User profile not found"));

			List<Activity> recentActivities = activityRepository.findActivitiesByUserAndDateAfter(user,
					LocalDateTime.now().minusDays(7));

			String prompt = promptBuilder.buildDailyAdvicePrompt(profile, recentActivities, metric, hadActivity);

			String aiResponse = geminiClient.generateText(prompt); // Ensure generateText exists in Client

			DailyAdviceResponse advice = objectMapper.readValue(aiResponse, DailyAdviceResponse.class);
			String message = advice.getFullMessage();
			
			// 3. Complete Job
			job.setAdviceText(message);
			job.setStatus(Status.COMPLETED);
			jobRepository.save(job);

			// 4. Notify User
			botService.sendMessage(viewService.getDiaryAdviceMessage(job.getUserChatId(), message));

		} catch (Exception e) {
			log.error("Job {} processing failed", jobId, e);
			job.setStatus(Status.FAILED);
			job.setErrorMessage(e.getMessage());
			jobRepository.save(job);

			// Optional: Send error notification to user using ViewService
			botService.sendMessage(viewService.getGeneralErrorMessage(job.getUserChatId()));
		}
	}

	private DailyMetric saveDailyMetric(DailyAdviceJob job) {
		DailyMetric metric = metricRepository.findMetricsByUserAndDateAfter(job.getUser(), job.getDate()).stream()
				.filter(m -> m.getDate().equals(job.getDate()))
				.findFirst()
				.orElse(new DailyMetric());

		metric.setUser(job.getUser());
		metric.setDate(job.getDate());
		metric.setSynthetic(false);
		metric.setSleepHours(job.getSleepHours());
		metric.setStressLevel(job.getStressLevel());

		int totalSteps = UserPhysiologyService.BASE_DAILY_STEPS;
		if (job.getHadActivity()) {
			totalSteps += UserPhysiologyService.ACTIVITY_BONUS_STEPS;
		}
		metric.setDailyBaseSteps(totalSteps);

		return metricRepository.save(metric);
	}

	private void saveActivity(DailyAdviceJob job) {
		Activity activity = new Activity();
		activity.setUser(job.getUser());
		activity.setSynthetic(false);

		// Date Logic
		LocalDateTime activityDate = LocalDateTime.now().getHour() < 12 
				? LocalDateTime.now().minusDays(1).withHour(19)
				: LocalDateTime.now().withHour(18);
		activity.setDateTime(activityDate);

		activity.setType(job.getActivityType());
		int duration = job.getDurationMinutes();
		activity.setDurationSeconds(duration * 60);

		int rpe = job.getRpe() != null ? job.getRpe() : 5;
		int estimatedPulse = 90 + (rpe * 8);
		activity.setAvgPulse(estimatedPulse);
		activity.setMaxPulse(estimatedPulse + 20);
		activity.setCaloriesBurned(duration * (rpe + 2));

		int steps = userPhysiologyService.calculateSteps(job.getActivityType(), job.getDurationMinutes(), rpe);
		activity.setActivitySteps(steps);
		// Rough distance approx
		activity.setDistanceMeters((int) (steps * 0.75));

		activityRepository.save(activity);
	}
}
