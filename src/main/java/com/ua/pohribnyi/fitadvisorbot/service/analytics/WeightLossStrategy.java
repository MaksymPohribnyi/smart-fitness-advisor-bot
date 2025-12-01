package com.ua.pohribnyi.fitadvisorbot.service.analytics;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.ua.pohribnyi.fitadvisorbot.enums.AnalyticsMetricType;
import com.ua.pohribnyi.fitadvisorbot.model.dto.analytics.PeriodReportDto.MetricResult;
import com.ua.pohribnyi.fitadvisorbot.model.entity.Activity;
import com.ua.pohribnyi.fitadvisorbot.model.entity.DailyMetric;
import com.ua.pohribnyi.fitadvisorbot.model.entity.user.User;
import com.ua.pohribnyi.fitadvisorbot.model.entity.user.UserProfile;
import com.ua.pohribnyi.fitadvisorbot.service.telegram.MessageService;
import com.ua.pohribnyi.fitadvisorbot.util.math.MetricThreshold;

@Component
public class WeightLossStrategy extends AbstractGoalStrategy{

    public WeightLossStrategy(MessageService messageService) {
        super(messageService);
    }

	// 1. Burn Level (Activity + NEAT Calories)
	// Thresholds increased because we now sum sources
	private static final List<MetricThreshold> BURN_VALUES = List.of(
			new MetricThreshold(0.0, "analytics.weight.burn.low"), // < 400
			new MetricThreshold(400.0, "analytics.weight.burn.moderate"), // 400 - 600
			new MetricThreshold(600.0, "analytics.weight.burn.high") // > 600
	);

	// 2. Active Days (Days per week with sufficient activity)
	// > 5 days/week shows excellent consistency
	private static final List<MetricThreshold> ACTIVE_DAYS_VALUES = List.of(
			new MetricThreshold(0.0, "analytics.weight.active_days.start"),
			new MetricThreshold(3.5, "analytics.weight.active_days.good"),
			new MetricThreshold(5.0, "analytics.weight.active_days.hero"));

    // 3. Peak Day (Max steps) - Status keys are generic as it's a factual record
    private static final List<MetricThreshold> PEAK_VALUES = List.of(
            new MetricThreshold(0.0, "analytics.weight.peak.any") 
    );

    // 4. Prediction: Speed of burning 7000 kcal (kg/month equivalent)
    // Calculated as: 30 / Days_to_burn_7000
	private static final List<MetricThreshold> PREDICT_VALUES = List.of(
			new MetricThreshold(0.0, "analytics.weight.predict.turbo"), // 0-12 days
			new MetricThreshold(12.0, "analytics.weight.predict.steady"), // 12-20 days
			new MetricThreshold(20.0, "analytics.weight.predict.slow") // > 20 days
	);

	private static final int ACTIVE_DAY_STEPS_THRESHOLD = 8000;
	private static final int ACTIVE_DAY_CALORIES_THRESHOLD = 300;
	
	private static final double TARGET_ACTIVE_DAYS = 5.0;   // 4 days/week (Solid consistency)
    private static final double TARGET_DAILY_BURN = 600.0;  // 500 kcal (Ambitious average)

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
			List<DailyMetric> dailyMetrics, Duration duration) {

		List<MetricResult> results = new ArrayList<>();
		String lang = user.getLanguageCode();

        long totalDays = Math.max(1, duration.toDays());

		// SINGLE SOURCE OF TRUTH: Calculate average daily burn once
		double avgDailyBurn = calculateUnifiedAvgDailyBurn(activities, dailyMetrics);
        
        // 1. Burn Level
        results.add(calcBurnLevel(avgDailyBurn, lang));

        // 2. Active Days
        results.add(calcActiveDays(activities, dailyMetrics, totalDays, lang));

        // 3. Peak Day
        results.add(calcPeakDay(dailyMetrics, lang));

        return results;
	}

	@Override
	public MetricResult calculatePredictionMetric(User user, UserProfile profile, List<Activity> activities,
			List<DailyMetric> dailyMetrics) {
		String lang = user.getLanguageCode();

		// SINGLE SOURCE OF TRUTH: Reuse same logic
        double avgDailyBurn = calculateUnifiedAvgDailyBurn(activities, dailyMetrics);
		double kcalInKg = UserPhysiologyService.getKcalInKgFat(); 
		
        if (avgDailyBurn < 100) avgDailyBurn = 100; // Safety floor

        // Days to burn 7000 kcal
        double daysToBurn7000 = kcalInKg / avgDailyBurn;
        
        return buildResultReverse(
                AnalyticsMetricType.TIME_TO_BURN_1KG,
                daysToBurn7000, 
                PREDICT_VALUES,
                "%.0f".formatted(daysToBurn7000), 
                lang,
                20, 12 
        );
    }

	@Override
	public int calculateConsistencyScore(User user, List<Activity> activities, List<DailyMetric> dailyMetrics,
			Duration duration, List<MetricResult> advancedMetrics) {
		// 1. Consistency (40%) - Target: 4.0 active days/week
		// Extract from metrics
		double activeDaysPerWeek = extractMetricValue(advancedMetrics, AnalyticsMetricType.ACTIVE_DAYS_WEEKLY);
		int activeScore = calcComponentScore(activeDaysPerWeek, TARGET_ACTIVE_DAYS, 40);

		// 2. Intensity/NEAT (30%) - Target: 500 kcal active burn
		// Extract from metrics
		double avgBurn = extractMetricValue(advancedMetrics, AnalyticsMetricType.AVG_DAILY_BURN);
		int burnScore = calcComponentScore(avgBurn, TARGET_DAILY_BURN, 30);

		// 3. Sleep (30%) - Target: 7.0h
		double avgSleep = getAverageSleep(dailyMetrics);
		int sleepScore = calcComponentScore(avgSleep, DEFAULT_SLEEP_GOAL, 30);

		return activeScore + burnScore + sleepScore;
	}
	
	private MetricResult calcBurnLevel(double avgDailyBurn, String lang) {
        // 1. Activity Calories
		return buildResult(
                AnalyticsMetricType.AVG_DAILY_BURN,
                avgDailyBurn,
                BURN_VALUES,
                "%.0f".formatted(avgDailyBurn),
                lang,
                400.0, 600.0
        );
    }
	
	private MetricResult calcActiveDays(List<Activity> activities, List<DailyMetric> dailyMetrics, long totalDays, String lang) {
        long activeDaysCount = countActiveDays(dailyMetrics, activities);
        double activeDaysPerWeek = ((double) activeDaysCount / totalDays) * DAYS_IN_WEEK;

        return buildResult(
                AnalyticsMetricType.ACTIVE_DAYS_WEEKLY,
                activeDaysPerWeek,
                ACTIVE_DAYS_VALUES,
                "%.0f".formatted(activeDaysPerWeek),
                lang,
                3.5, 5.0
        );
    }
	
	private MetricResult calcPeakDay(List<DailyMetric> dailyMetrics, String lang) {
        int maxSteps = dailyMetrics.stream()
                .mapToInt(DailyMetric::getDailyBaseSteps)
                .max().orElse(0);
        
        // Always return "Good" status (positive reinforcement for personal record)
        return buildResult(
                AnalyticsMetricType.PEAK_STEPS_DAY,
                maxSteps, // Value for threshold picking
                PEAK_VALUES,
                String.format("%,d", maxSteps).replace(',', ' '), // Format: 14 200
                lang,
                0.0, 0.0 // Always green
        );
    }
	
	/**
     * Counts days where User met EITHER the steps threshold OR the calories threshold.
     */
    private long countActiveDays(List<DailyMetric> metrics, List<Activity> activities) {
        // Map dates to sum of calories for that day
        Map<LocalDate, Integer> caloriesByDate = activities.stream()
                .collect(Collectors.groupingBy(
                        a -> a.getDateTime().toLocalDate(),
                        Collectors.summingInt(Activity::getCaloriesBurned)
                ));

        return metrics.stream()
        		.filter(d -> {
        			boolean stepsOk = d.getDailyBaseSteps() != null && d.getDailyBaseSteps() >= ACTIVE_DAY_STEPS_THRESHOLD;
        			boolean calsOk = caloriesByDate.getOrDefault(d.getDate(), 0) >= ACTIVE_DAY_CALORIES_THRESHOLD;
        			return stepsOk || calsOk;
        			})
        		.count();
    }
    
    /**
     * Unified logic for calculating Average Daily Active Burn (Activities + NEAT).
     * Uses actual data points count (dailyMetrics.size) to be accurate even if data has gaps.
     */
    private double calculateUnifiedAvgDailyBurn(List<Activity> activities, List<DailyMetric> dailyMetrics) {
        if (dailyMetrics.isEmpty()) return 0.0;

        double kcalPerStep = UserPhysiologyService.getKcalPerStep();
        double totalWorkoutCalories = activities.stream().mapToInt(Activity::getCaloriesBurned).sum();
        double totalNeatCalories = dailyMetrics.stream()
                .mapToInt(DailyMetric::getDailyBaseSteps)
                .sum() * kcalPerStep;

        // Use actual data count, not report duration, to get true intensity per recorded day
        long dataPoints = dailyMetrics.size(); 
        return (totalWorkoutCalories + totalNeatCalories) / dataPoints;
    }

}