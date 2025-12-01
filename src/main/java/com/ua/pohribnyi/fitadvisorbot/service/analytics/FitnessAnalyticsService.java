package com.ua.pohribnyi.fitadvisorbot.service.analytics;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ua.pohribnyi.fitadvisorbot.model.dto.analytics.PeriodReportDto;
import com.ua.pohribnyi.fitadvisorbot.model.dto.analytics.PeriodReportDto.MetricResult;
import com.ua.pohribnyi.fitadvisorbot.model.entity.Activity;
import com.ua.pohribnyi.fitadvisorbot.model.entity.DailyMetric;
import com.ua.pohribnyi.fitadvisorbot.model.entity.user.User;
import com.ua.pohribnyi.fitadvisorbot.model.entity.user.UserProfile;
import com.ua.pohribnyi.fitadvisorbot.repository.data.ActivityRepository;
import com.ua.pohribnyi.fitadvisorbot.repository.data.DailyMetricRepository;
import com.ua.pohribnyi.fitadvisorbot.repository.user.UserProfileRepository;
import com.ua.pohribnyi.fitadvisorbot.service.telegram.MessageService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class FitnessAnalyticsService {

	private final ActivityRepository activityRepository;
	private final DailyMetricRepository dailyMetricRepository;
	private final UserProfileRepository userProfileRepository;
	private final List<GoalAnalyticsStrategy> strategies;

	@Transactional(readOnly = true)
	public PeriodReportDto generateOnboardingReport(User user) {
		return generateReport(user, Duration.ofDays(60), "analytics.report.period.initial");
	}

	@Transactional(readOnly = true)
	public PeriodReportDto generateReport(User user, Duration duration, String periodKey) {
		LocalDateTime sinceDateTime = LocalDateTime.now().minus(duration);
		LocalDate sinceDate = LocalDate.now().minusDays(duration.toDays());

		UserProfile profile = userProfileRepository.findByUser(user)
                .orElseThrow(() -> new IllegalStateException("UserProfile required for analytics generation"));
		
		// 1. Base Metrics
		List<Activity> activities = activityRepository.findActivitiesByUserAndDateAfter(user, sinceDateTime);
		List<DailyMetric> dailyMetrics = dailyMetricRepository.findMetricsByUserAndDateAfter(user, sinceDate);

		
		int totalActivities = activities.size();
		double totalDistKm = activities.stream().mapToDouble(Activity::getDistanceMeters).sum() / 1000.0;
		double totalDurationHours = activities.stream().mapToDouble(Activity::getDurationSeconds).sum() / 3600.0;

		// 2. Determine Strategy
		String goal = profile.getGoal() != null ? profile.getGoal() : "health";

		GoalAnalyticsStrategy strategy = strategies.stream()
				.filter(s -> s.supports(goal))
				.findFirst()
				.orElse(strategies.stream()
						.filter(s -> s.supports("lose_weight"))
						.findFirst()
						.orElseThrow(() -> new IllegalStateException("No strategies available"))); 

		// 3. Metrics Calculation
		List<MetricResult> baseMetrics = strategy.calculateBaseMetrics(user, activities, dailyMetrics, duration);

		List<MetricResult> advancedMetrics = strategy.calculateMetrics(user, profile, activities, dailyMetrics, duration);

		MetricResult predictionMetric = strategy.calculatePredictionMetric(user, profile, activities, dailyMetrics);
		
		// 4. Consistency & Advisor
		int consistencyScore = strategy.calculateConsistencyScore(user, activities, dailyMetrics, duration, advancedMetrics);
		String advisorKey = strategy.getAdvisorSummaryKey(consistencyScore);

		return PeriodReportDto.builder()
				.periodKey(periodKey)
				.goalName(strategy.getGoalTitleKey())
				.totalActivities(totalActivities)
				.totalDistanceKm(totalDistKm)
				.totalDurationHours(totalDurationHours)
				.consistencyScore(consistencyScore)
				.consistencyVerdictKey(getConsistencyVerdictKey(consistencyScore))
				.baseMetrics(baseMetrics)
				.advancedMetrics(advancedMetrics)
				.predictionMetric(predictionMetric)
				.advisorSummaryKey(advisorKey)
				.build();
	}

	private String getConsistencyVerdictKey(int score) {
		if (score >= 90)
			return "analytics.consistency.iron";
		if (score >= 70)
			return "analytics.consistency.good";
		if (score >= 40)
			return "analytics.consistency.start";
		return "analytics.consistency.lazy";
	}
}
