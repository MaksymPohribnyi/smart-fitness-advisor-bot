package com.ua.pohribnyi.fitadvisorbot.service.analytics;

import java.util.List;

import com.ua.pohribnyi.fitadvisorbot.model.dto.analytics.PeriodReportDto.MetricResult;
import com.ua.pohribnyi.fitadvisorbot.model.entity.Activity;
import com.ua.pohribnyi.fitadvisorbot.model.entity.DailyMetric;
import com.ua.pohribnyi.fitadvisorbot.model.entity.user.User;

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

	/**
	 * Calculates specific advanced metrics based on the goal.
	 */
	List<MetricResult> calculateMetrics(User user, List<Activity> activities, List<DailyMetric> dailyMetrics);

	/**
	 * Returns the key for the goal title in messages.yml.
	 */
	String getGoalTitleKey();
}