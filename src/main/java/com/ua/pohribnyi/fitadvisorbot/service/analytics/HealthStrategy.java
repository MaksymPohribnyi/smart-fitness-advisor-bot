package com.ua.pohribnyi.fitadvisorbot.service.analytics;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
public class HealthStrategy extends AbstractGoalStrategy {

	public HealthStrategy(MessageService messageService) {
		super(messageService);
	}

	// 1. Active Load (Equivalent Steps per Day)
	private static final List<MetricThreshold> LOAD_VALUES = List.of(
			new MetricThreshold(0.0, "analytics.health.load.low"),       // < 75
            new MetricThreshold(75.0, "analytics.health.load.base"),     // 75-150
            new MetricThreshold(150.0, "analytics.health.load.optimal"), // 150-300
            new MetricThreshold(300.0, "analytics.health.load.athlete"));

	// 2. Zen Day (Minimum Stress) - Always Green/Positive
	private static final List<MetricThreshold> ZEN_VALUES = List
			.of(new MetricThreshold(0.0, "analytics.health.zen.any"));

	// 3. Restorative Nights: Nights/week > 7.5h
	// 5+ nights is excellent consistency
	private static final List<MetricThreshold> SLEEP_VALUES = List.of(
			new MetricThreshold(0.0, "analytics.health.sleep_freq.deficit"), // < 3
			new MetricThreshold(3.0, "analytics.health.sleep_freq.base"), // 3 - 5
			new MetricThreshold(5.0, "analytics.health.sleep_freq.optimal") // > 5
	);

	// 4. Predictor: Health Streak (Higher is Better)
	private static final List<MetricThreshold> STREAK_VALUES = List.of(
			new MetricThreshold(0.0, "analytics.health.predict.start"), // 0 days
			new MetricThreshold(3.0, "analytics.health.predict.building"), // 3+ days
			new MetricThreshold(7.0, "analytics.health.predict.titan") // 7+ days
	);

	private static final int STEPS_BASELINE = 3000; // "Junk" steps threshold
    private static final double STEPS_TO_MINUTES_RATIO = 110.0; // Brisk walk cadence
	private static final double SPORT_TO_STEPS_RATIO = 150.0; // 1 min sport = 150 steps
	
	// "Ideal Day" Criteria for Streak (Hard Mode)
    private static final int STREAK_MIN_EQ_STEPS = 10000; 
    private static final int STREAK_STRESS_LIMIT = 2; // Matches "< 3.0"
    
    
	@Override
	public boolean supports(String goalCode) {
		return "health".equalsIgnoreCase(goalCode);
	}

	@Override
	public String getGoalTitleKey() {
		return "onboarding.goal.health";
	}

	@Override
	public List<MetricResult> calculateMetrics(User user, UserProfile profile, List<Activity> activities,
			List<DailyMetric> dailyMetrics, Duration duration) {
		List<MetricResult> results = new ArrayList<>();
		
		String lang = user.getLanguageCode();
		long reportDurationDays = Math.max(1, duration.toDays());
		double weeks = getWeeks(duration);

		// 1. WHO Activity Compliance
		results.add(calcActiveLoad(activities, dailyMetrics, weeks, lang));

		// 2. Zen Day (Best Stress Day)
		results.add(calcZenDay(dailyMetrics, lang));

        // 3. Restorative Nights (Frequency)
		results.add(calcRestorativeNights(dailyMetrics, reportDurationDays, lang));
		
		return results;
	}

	@Override
	public MetricResult calculatePredictionMetric(User user, UserProfile profile, List<Activity> activities,
			List<DailyMetric> dailyMetrics) {

		String lang = user.getLanguageCode();
		LocalDate today = LocalDate.now();

		// Map dates to Activity Duration
		Map<LocalDate, Integer> activityMinutesMap = activities.stream()
				.collect(Collectors.groupingBy(
						a -> a.getDateTime().toLocalDate(), 
						Collectors.summingInt(a -> a.getDurationSeconds() / 60)
						));

		Map<LocalDate, DailyMetric> metricsMap = dailyMetrics.stream()
                .collect(Collectors.toMap(DailyMetric::getDate, Function.identity(), (e1, e2) -> e1));

		
		// 2. Check Today (Bonus)
        // If today is “green,” we add +1. If not, it’s okay — the streak doesn’t break.
        boolean isTodayGreen = isGreenDay(today, metricsMap, activityMinutesMap);

		// 3. Calculate History Streak
        long historyStreak = Stream.iterate(today.minusDays(1), date -> date.minusDays(1))
                .takeWhile(date -> isGreenDay(date, metricsMap, activityMinutesMap))
                .count();

        int totalStreak = (int) historyStreak + (isTodayGreen ? 1 : 0);

        return buildResult(
                AnalyticsMetricType.ENERGY_STREAK,
                (double) totalStreak,
                STREAK_VALUES,
                "%d".formatted(totalStreak),
                lang,
                3.0, 7.0 
        );
    }
	
	@Override
	public int calculateConsistencyScore(User user, List<Activity> activities, List<DailyMetric> dailyMetrics,
			Duration duration, List<MetricResult> advancedMetrics) {

		double weeklyMinutes = extractMetricValue(advancedMetrics, AnalyticsMetricType.ACTIVE_LOAD);
		// Fallback if metric not found (e.g. error): Estimate based on daily metrics NEAT
		if (weeklyMinutes == 0.0 && !dailyMetrics.isEmpty()) {
			double weeks = getWeeks(duration);
			weeklyMinutes = calcActiveLoad(activities, dailyMetrics, weeks, user.getLanguageCode()).getRawValue();
		}
		
		int volumeScore = calcComponentScore(weeklyMinutes, SPORT_TO_STEPS_RATIO, 40);

		// 2. Sleep (30%) - Target: 7.0h Avg
		double avgSleep = getAverageSleep(dailyMetrics);
		int sleepScore = calcComponentScore(avgSleep, DEFAULT_SLEEP_GOAL, 30);

		// 3. Stress (30%) - Target: <= 2.5 (Low Stress)
		double avgStress = getAverageStress(dailyMetrics);
		// Inverse logic: 2.5 -> 100%, 5.0 -> 0%
		double stressPerformance = Math.max(0, (5.0 - avgStress) / (5.0 - LOW_STRESS_THRESHOLD)); 
		int stressScore = (int) (Math.min(1.0, stressPerformance) * 30);

		return volumeScore + sleepScore + stressScore;
	}

	private MetricResult calcActiveLoad(List<Activity> activities, List<DailyMetric> dailyMetrics, double weeks,
			String lang) {
		// 1. Total Base Steps
		double workoutMinutes = activities.stream().mapToLong(Activity::getDurationSeconds).sum() / 60.0;

		// 2. "Smart" Walking Minutes
		double effectiveWalkingMinutes = calcNEATSteps(dailyMetrics) / STEPS_TO_MINUTES_RATIO;

		// Total Weekly Average
		double weeklyTotal = (workoutMinutes + effectiveWalkingMinutes) / weeks;

		return buildResult(
				AnalyticsMetricType.ACTIVE_LOAD, 
				weeklyTotal, 
				LOAD_VALUES,
				"%.0f".formatted(weeklyTotal), 
				lang, 
				150.0, 300.0 // Good > 150, Excellent > 300
		);
	}

	private MetricResult calcZenDay(List<DailyMetric> dailyMetrics, String lang) {
        // Find day with MINIMUM stress
        DailyMetric bestDay = dailyMetrics.stream()
                .min(Comparator.comparingInt(DailyMetric::getStressLevel))
                .orElse(null);

        String valueText = "N/A";
        if (bestDay != null) {
            Locale locale = "uk".equals(lang) ? Locale.of("uk", "UA") : Locale.ENGLISH;
            String dayName = bestDay.getDate().getDayOfWeek().getDisplayName(TextStyle.FULL, locale);
            // Capitalize first letter
            dayName = dayName.substring(0, 1).toUpperCase() + dayName.substring(1);
            
            valueText = "%s".formatted(dayName);
        }

        return buildResult(
                AnalyticsMetricType.ZEN_DAY,
                0.0, // Value ignored for status (always green)
                ZEN_VALUES,
                valueText,
                lang,
                -1.0, -1.0 // Always Green
        );
    }

	private MetricResult calcRestorativeNights(List<DailyMetric> dailyMetrics, long totalDays, String lang) {
        long goodSleepDays = dailyMetrics.stream()
                .filter(d -> d.getSleepHours() != null && d.getSleepHours() >= DEFAULT_SLEEP_GOAL)
                .count();
        
        double validDays = Math.min(goodSleepDays, totalDays);
        double nightsPerWeek = (validDays / totalDays) * DAYS_IN_WEEK;

        return buildResult(
                AnalyticsMetricType.RESTORATIVE_NIGHTS,
                nightsPerWeek,
                SLEEP_VALUES,
                "%.0f".formatted(nightsPerWeek),
                lang,
                3.0, 5.0
        );
    }
	
	private int calcNEATSteps(List<DailyMetric> dailyMetrics) {
		return dailyMetrics.stream()
				.mapToInt(dm -> {
					int totalSteps = dm.getDailyBaseSteps() != null ? dm.getDailyBaseSteps() : 0;
					int effectiveSteps = Math.max(0, totalSteps - STEPS_BASELINE);
					return effectiveSteps;
					})
				.sum();
	}
	
	/**
	 * Validates if a specific day meets all "Green Day" criteria.
	 */
	private boolean isGreenDay(LocalDate date, Map<LocalDate, DailyMetric> metricsMap,
			Map<LocalDate, Integer> activityMap) {
		// Fail fast if no data for the day
		if (!metricsMap.containsKey(date))
			return false;

		DailyMetric dm = metricsMap.get(date);
		int sportMinutes = activityMap.getOrDefault(date, 0);

		// Extract values with defaults
		int baseSteps = dm.getDailyBaseSteps() != null ? dm.getDailyBaseSteps() : 0;
		double sleep = dm.getSleepHours() != null ? dm.getSleepHours() : 0.0;
		int stress = dm.getStressLevel() != null ? dm.getStressLevel() : 5; // Pessimistic default

		// Calculate Logic
		double eqSteps = baseSteps + (sportMinutes * SPORT_TO_STEPS_RATIO);

		return eqSteps >= STREAK_MIN_EQ_STEPS && sleep >= DEFAULT_SLEEP_GOAL && stress <= STREAK_STRESS_LIMIT;
	}

}