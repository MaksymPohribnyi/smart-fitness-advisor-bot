package com.ua.pohribnyi.fitadvisorbot.service.analytics;

import java.time.Duration;
import java.util.List;

import com.ua.pohribnyi.fitadvisorbot.model.dto.analytics.PeriodReportDto.MetricResult;
import com.ua.pohribnyi.fitadvisorbot.model.entity.Activity;
import com.ua.pohribnyi.fitadvisorbot.model.entity.DailyMetric;
import com.ua.pohribnyi.fitadvisorbot.model.entity.user.User;
import com.ua.pohribnyi.fitadvisorbot.model.entity.user.UserProfile;

/**
 * Strategy interface for goal-oriented analytics. Each implementation handles a
 * specific UserProfile goal.
 */
public interface GoalAnalyticsStrategy {

	/**
	 * Checks if this strategy supports the given goal code.
	 * 
	 * @param goalCode e.g., "lose_weight", "run_10k"
	 */
	boolean supports(String goalCode);
	
	String getGoalTitleKey();

	List<MetricResult> calculateBaseMetrics(User user, List<Activity> activities, List<DailyMetric> dailyMetrics,
			Duration duration);

	/**
	 * Calculates specific advanced metrics based on the goal. * @param duration The
	 * time period covered by this report (e.g. 7 days, 60 days). Crucial for
	 * calculating averages and volumes correctly.
	 */
	List<MetricResult> calculateMetrics(User user, UserProfile userProfile, List<Activity> activities,
			List<DailyMetric> dailyMetrics, Duration duration);

	/**
	 * Calculates the single most important prediction metric for the goal. E.g.,
	 * Race Predictor for runners, Calorie Forecast for weight loss.
	 */
	MetricResult calculatePredictionMetric(User user, UserProfile profile, List<Activity> activities,
			List<DailyMetric> dailyMetrics);

	/**
	 * Calculates consistency score using ALREADY calculated metrics to avoid
	 * redundancy.
	 */
	int calculateConsistencyScore(User user, List<Activity> activities, List<DailyMetric> dailyMetrics,
			Duration duration, List<MetricResult> advancedMetrics);

	/**
	 * Returns a message key for the Smart Advisor summary based on user
	 * consistency. Focuses on behavioral framing rather than specific workout
	 * advice.
	 */
	String getAdvisorSummaryKey(double consistencyScore);
}
