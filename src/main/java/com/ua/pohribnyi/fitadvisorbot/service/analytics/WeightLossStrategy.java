package com.ua.pohribnyi.fitadvisorbot.service.analytics;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.ua.pohribnyi.fitadvisorbot.model.dto.analytics.PeriodReportDto.MetricResult;
import com.ua.pohribnyi.fitadvisorbot.model.entity.Activity;
import com.ua.pohribnyi.fitadvisorbot.model.entity.DailyMetric;
import com.ua.pohribnyi.fitadvisorbot.model.entity.user.User;
import com.ua.pohribnyi.fitadvisorbot.model.entity.user.UserProfile;
import com.ua.pohribnyi.fitadvisorbot.model.enums.AnalyticsMetricType;
import com.ua.pohribnyi.fitadvisorbot.repository.user.UserProfileRepository;
import com.ua.pohribnyi.fitadvisorbot.service.telegram.MessageService;
import com.ua.pohribnyi.fitadvisorbot.util.math.MathUtils;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class WeightLossStrategy implements GoalAnalyticsStrategy {

	private final UserPhysiologyService physiologyService;
	private final UserProfileRepository userProfileRepository;
	private final MessageService messageService;

	private static final int MIN_ACTIVITY_DURATION_SEC = 1200;

	@Override
	public boolean supports(String goalCode) {
		return "lose_weight".equalsIgnoreCase(goalCode);
	}

	@Override
	public String getGoalTitleKey() {
		return "onboarding.goal.lose_weight";
	}

	@Override
	public List<MetricResult> calculateMetrics(User user, List<Activity> activities, List<DailyMetric> dailyMetrics) {
		List<MetricResult> results = new ArrayList<>();
		String lang = user.getLanguageCode();

		UserProfile profile = userProfileRepository.findByUser(user)
				.orElseThrow(() -> new IllegalStateException("UserProfile required"));

		var fatBurnZone = physiologyService.calculateFatBurnZone(profile);

		// 1. Fat-Fuel Consistency
		long fatBurnDays = activities.stream().filter(a -> a.getDurationSeconds() >= MIN_ACTIVITY_DURATION_SEC)
				.filter(a -> fatBurnZone.contains(a.getAvgPulse())).count();

		long totalDays = Math.max(dailyMetrics.size(), 1);

		results.add(MetricResult.builder().type(AnalyticsMetricType.FAT_FUEL_CONSISTENCY)
				.formattedValue(String.format("%d / %d", fatBurnDays, totalDays))
				.statusEmoji(getConsistencyEmoji(fatBurnDays, totalDays, lang)).build());

		// 2. Metabolic Momentum
		List<Double> stepsHistory = dailyMetrics.stream().map(d -> (double) d.getDailyBaseSteps()).toList();
		double slope = MathUtils.calculateSlope(stepsHistory);

		String trendEmoji = slope > 5 ? messageService.getMessage("analytics.status.good", lang)
				: (slope < -5 ? messageService.getMessage("analytics.status.bad", lang)
						: messageService.getMessage("analytics.status.avg", lang));

		results.add(MetricResult.builder().type(AnalyticsMetricType.METABOLIC_MOMENTUM)
				.formattedValue(String.format("%.1f", slope)).statusEmoji(trendEmoji).build());

		// 3. Recovery-to-Burn Ratio
		double avgSleep = dailyMetrics.stream().mapToDouble(DailyMetric::getSleepHours).average().orElse(0);
		double avgCals = activities.stream().mapToDouble(Activity::getCaloriesBurned).average().orElse(1);

		double ratio = MathUtils.safeDivide(avgSleep, avgCals / 100.0);

		String ratioEmoji = ratio > 1.2 ? messageService.getMessage("analytics.status.good", lang)
				: messageService.getMessage("analytics.status.bad", lang);

		results.add(MetricResult.builder().type(AnalyticsMetricType.RECOVERY_TO_BURN)
				.formattedValue(String.format("%.1f", ratio)).statusEmoji(ratioEmoji).build());

		return results;
	}

	private String getConsistencyEmoji(long activeDays, long totalDays, String lang) {
		double ratio = (double) activeDays / totalDays;
		if (ratio > 0.4)
			return messageService.getMessage("analytics.status.good", lang);
		if (ratio > 0.2)
			return messageService.getMessage("analytics.status.avg", lang);
		return messageService.getMessage("analytics.status.bad", lang);
	}
}