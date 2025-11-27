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

	List<MetricResult> calculateBaseMetrics(User user, List<Activity> activities, List<DailyMetric> dailyMetrics, Duration duration);
	
	
	/**
	 * Calculates specific advanced metrics based on the goal.
	 * * @param duration The time period covered by this report (e.g. 7 days, 60 days).
	 * Crucial for calculating averages and volumes correctly.
	 */
	List<MetricResult> calculateMetrics(User user, UserProfile userProfile, List<Activity> activities,
			List<DailyMetric> dailyMetrics, Duration duration);

	/**
	 * Returns the key for the goal title in messages.yml.
	 */
	String getGoalTitleKey();

	/**
	 * Returns a message key for expert praise based on metrics context.
	 */
	String getExpertPraiseKey(double consistencyScore, List<MetricResult> metrics);

	/**
	 * Returns a message key for expert action/challenge based on metrics context.
	 */
	String getExpertActionKey(double consistencyScore, List<MetricResult> metrics);
}