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
import com.ua.pohribnyi.fitadvisorbot.service.telegram.MessageService;
import com.ua.pohribnyi.fitadvisorbot.util.math.MathUtils;
import com.ua.pohribnyi.fitadvisorbot.util.math.MetricThreshold;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class WeightLossStrategy implements GoalAnalyticsStrategy {

	private final UserPhysiologyService physiologyService;
	private final MessageService messageService;

	private static final int MIN_ACTIVITY_DURATION_SEC = 1200;

	// 1. Fat-Fuel (Ratio -> Level Key)
	private static final List<MetricThreshold> FAT_FUEL_VALUES = List.of(
            new MetricThreshold(0.0, "analytics.level.initial"),
            new MetricThreshold(0.2, "analytics.level.developing"),
            new MetricThreshold(0.5, "analytics.level.optimal")
    );

	// 2. Momentum (Slope -> Trend Key)
	private static final List<MetricThreshold> MOMENTUM_VALUES = List.of(
            new MetricThreshold(-Double.MAX_VALUE, "analytics.trend.down"),
            new MetricThreshold(-10.0, "analytics.trend.stable"),
            new MetricThreshold(10.0, "analytics.trend.up")
    );

	// 3. Recovery (Ratio -> Level Key)
	private static final List<MetricThreshold> RECOVERY_VALUES = List.of(
            new MetricThreshold(0.0, "analytics.level.low"),
            new MetricThreshold(1.0, "analytics.level.normal"),
            new MetricThreshold(1.5, "analytics.level.excellent")
    );

	@Override
	public boolean supports(String goalCode) {
		return "lose_weight".equalsIgnoreCase(goalCode);
	}

	@Override
	public String getGoalTitleKey() {
		return "onboarding.goal.lose_weight";
	}

	@Override
	public List<MetricResult> calculateMetrics(User user, UserProfile profile, List<Activity> activities,
			List<DailyMetric> dailyMetrics) {

		List<MetricResult> results = new ArrayList<>();
		String lang = user.getLanguageCode();

		// 1. Fat-Fuel Consistency
		results.add(calcFatFuel(profile, activities, lang));

		// 2. Metabolic Momentum
		results.add(calcMetabolicMomentum(activities, dailyMetrics, lang));

		// 3. Recovery-to-Burn Ratio
		results.add(calcRecoveryRatio(activities, dailyMetrics, lang));

		return results;
	}

	
	private MetricResult calcFatFuel(UserProfile profile, List<Activity> activities, String lang) {
		var fatBurnZone = physiologyService.calculateFatBurnZone(profile);
		long fatBurnDays = activities.stream()
				.filter(a -> a.getDurationSeconds() >= MIN_ACTIVITY_DURATION_SEC)
				.filter(a -> fatBurnZone.contains(a.getAvgPulse()))
				.count();

		int totalActivities = Math.max(activities.size(), 1);
        double fatBurnRatio = (double) fatBurnDays / totalActivities;
		
        return buildResult(
                AnalyticsMetricType.FAT_FUEL_CONSISTENCY,
                fatBurnRatio,
                FAT_FUEL_VALUES,
                "%.0f%%".formatted(fatBurnRatio * 100),
                lang,
                0.2, 0.5 // Thresholds: Avg >= 0.2, Good >= 0.5
        );
	}
	
	private MetricResult calcMetabolicMomentum(List<Activity> activities, List<DailyMetric> dailyMetrics, String lang) {
		List<Double> stepsHistory = dailyMetrics.stream()
                .map(d -> (double) d.getDailyBaseSteps())
                .toList();
        double slope = MathUtils.calculateSlope(stepsHistory);

        return buildResult(
                AnalyticsMetricType.METABOLIC_MOMENTUM,
                slope,
                MOMENTUM_VALUES,
                "%+.0f".formatted(slope),
                lang,
                -10.0, 10.0 // Thresholds: Avg >= -10, Good >= 10
        );
	}
	
	private MetricResult calcRecoveryRatio(List<Activity> activities, List<DailyMetric> dailyMetrics, String lang) {
		double avgSleep = dailyMetrics.stream().mapToDouble(DailyMetric::getSleepHours).average().orElse(0);
		double avgCals = activities.stream().mapToDouble(Activity::getCaloriesBurned).average().orElse(1);
		double ratio = (avgSleep / (avgCals / 100.0));

		return buildResult(
                AnalyticsMetricType.RECOVERY_TO_BURN,
                ratio,
                RECOVERY_VALUES,
                "%.1f".formatted(ratio),
                lang,
                1.0, 1.5 // Thresholds: Avg >= 1.0, Good >= 1.5
        );
	}

	private MetricResult buildResult(AnalyticsMetricType type, double value, List<MetricThreshold> rules,
			String formattedNumb, String lang, double avgThreshold, double goodThreshdold) {

		// 1. Pick Value Text
		String valueKey = MetricThreshold.pick(value, rules);
		String valueText = messageService.getMessage(valueKey, lang);

		// 2. Determine Status Emoji (Standard Logic)
		return MetricResult.builder()
				.type(type)
				.formattedValue(String.format("%s (%s)", valueText, formattedNumb))
				.statusEmoji(getStatusEmoji(value, avgThreshold, goodThreshdold, lang))
				.build();
	}
	
	private String getStatusEmoji(double value, double avg, double good, String lang) {
		String code = value >= good ? "analytics.status.good"
				: value >= avg ? "analytics.status.avg" : "analytics.status.bad";
		return messageService.getMessage(code, lang);
	}
}