package com.ua.pohribnyi.fitadvisorbot.service.analytics;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

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
public class MuscleBuildingStrategy extends AbstractGoalStrategy{

	// 1. Strength Workload: Minutes/week in 120-155 bpm zone
	private static final List<MetricThreshold> WORKLOAD_VALUES = List.of(
			new MetricThreshold(0.0, "analytics.muscle.workload.low"),
			new MetricThreshold(90.0, "analytics.muscle.workload.base"), // > 1.5h
			new MetricThreshold(150.0, "analytics.muscle.workload.optimal") // > 3h
	);

	// 2. Frequency: Sessions/week
	private static final List<MetricThreshold> FREQ_VALUES = List.of(
			new MetricThreshold(0.0, "analytics.muscle.freq.low"),
			new MetricThreshold(2.5, "analytics.muscle.freq.good"), // ~3 sessions
			new MetricThreshold(3.5, "analytics.muscle.freq.high") // 4+ sessions
	);

	// 3. Anabolic Sleep: nights / week
	private static final List<MetricThreshold> SLEEP_VALUES = List.of(
			new MetricThreshold(0.0, "analytics.muscle.sleep.low"),
			new MetricThreshold(4.0, "analytics.muscle.sleep.base"),
			new MetricThreshold(5.0, "analytics.muscle.sleep.optimal"));

	// 4. Predictor: Days to finish 300-min block
	private static final List<MetricThreshold> PREDICT_VALUES = List.of(
			new MetricThreshold(0.0, "analytics.muscle.predict.close"), // < 3 days
			new MetricThreshold(3.0, "analytics.muscle.predict.steady"), // 3-10 days
			new MetricThreshold(10.0, "analytics.muscle.predict.slow") // > 10 days
	);

	private static final int HYPERTROPHY_BLOCK_MINUTES = 300;
	private static final int MIN_AVG_PULSE_FLOOR = 100; // Filter out lazy walks
    private static final int MIN_MAX_PULSE_PEAK = 130;  // Evidence of heavy exertion
	private static final double MIN_SLEEP_HOURS = 7.5;
	
	// Explicit Strength Types (Strava mapping)
    private static final List<String> STRENGTH_TYPES = List.of(
            "WeightTraining", "Workout", "Strength", "Gym", "Crossfit", "HIIT", "CircuitTraining"
    );
	
	// Explicitly exclude cardio types to prevent false positives in Strength Workload
    private static final List<String> CARDIO_TYPES = List.of(
            "Run", "Ride", "Swim", "Hike", "Walk", "Elliptical", "NordicSki"
    );

	public MuscleBuildingStrategy(MessageService messageService) {
		super(messageService);
	}
	
    @Override
    public boolean supports(String goalCode) {
        return "build_muscle".equalsIgnoreCase(goalCode);
    }

    @Override
    public String getGoalTitleKey() {
        return "onboarding.goal.build_muscle";
    }

    @Override
	public List<MetricResult> calculateMetrics(User user, UserProfile profile, List<Activity> activities,
			List<DailyMetric> dailyMetrics, Duration duration) {

		List<MetricResult> results = new ArrayList<>();
        String lang = user.getLanguageCode();
        long totalDays = Math.max(1, duration.toDays());
        double weeks = totalDays / DAYS_IN_WEEK;

		List<Activity> workouts = activities.stream().filter(this::isStrengthSession).toList();
        
        // 1. Strength Workload
        results.add(calcStrengthWorkload(workouts, weeks, lang));

        // 2. Growth Frequency
        results.add(calcFrequency(workouts, weeks, lang));

        // 3. Anabolic Sleep
        results.add(calcAnabolicSleep(dailyMetrics, lang));

        return results;
    }
    
    
	@Override
	public MetricResult calculatePredictionMetric(User user, UserProfile profile, List<Activity> activities,
			List<DailyMetric> dailyMetrics) {
		String lang = user.getLanguageCode();

        // Total accumulated minutes in hypertrophy zone
        long totalStrengthMinutes = activities.stream()
                .filter(this::isStrengthSession)
                .mapToLong(Activity::getDurationSeconds)
                .sum() / 60;
        
		// Protection: If total volume is very low (< 60 min), show "Start" status
		// to avoid predicting "280 days to finish"
		if (totalStrengthMinutes < 60) {
			return MetricResult.builder()
					.type(AnalyticsMetricType.HYPERTROPHY_BLOCK_LEFT)
					.formattedValue(messageService.getMessage("analytics.muscle.predict.start", lang))
					.statusEmoji(messageService.getMessage("analytics.status.avg", lang)) 
					.build();
		}

        // Current progress in the block
        long currentBlockProgress = totalStrengthMinutes % HYPERTROPHY_BLOCK_MINUTES;
        long remainingMinutes = HYPERTROPHY_BLOCK_MINUTES - currentBlockProgress;

        // Daily Pace
        long totalDays = Math.max(1, dailyMetrics.size());
        double dailyPaceMinutes = (double) totalStrengthMinutes / totalDays;
        
        if (dailyPaceMinutes < 1.0) dailyPaceMinutes = 1.0; // Floor to avoid div/0

        double daysToFinish = remainingMinutes / dailyPaceMinutes;

        // Use REVERSE builder: Lower days = Better status
        return buildResultReverse(
                AnalyticsMetricType.HYPERTROPHY_BLOCK_LEFT,
                daysToFinish,
                PREDICT_VALUES,
                "%.0f".formatted(daysToFinish), // "Close cycle in ~3 days"
                lang,
                10.0, 
                3.0 
        );
    }
    
	@Override
	public int calculateConsistencyScore(User user, List<Activity> activities, List<DailyMetric> dailyMetrics,
			Duration duration, List<MetricResult> advancedMetrics) {

		// 1. Strength Frequency (40%) - Target 3 sessions/week
		double freqPerWeek = extractMetricValue(advancedMetrics, AnalyticsMetricType.GROWTH_FREQUENCY);
		int freqScore = calcComponentScore(freqPerWeek, 3.0, 40);

		// 2. Sleep (40%) - Target 5.0h/week (Critical for Hypertrophy)
		double avgSleep = extractMetricValue(advancedMetrics, AnalyticsMetricType.ANABOLIC_SLEEP);
		int sleepScore = calcComponentScore(avgSleep, 5.0, 40);

		// 3. Volume (20%) - Target 150 mins/week
		double workload = extractMetricValue(advancedMetrics, AnalyticsMetricType.STRENGTH_WORKLOAD);
		int volumeScore = calcComponentScore(workload, 150.0, 20);

		return freqScore + sleepScore + volumeScore;
	}
	
	private MetricResult calcStrengthWorkload(List<Activity> activities, double weeks, String lang) {
        // Sum duration of ALL effective strength sessions
        long strengthSeconds = activities.stream()
                .mapToLong(Activity::getDurationSeconds)
                .sum();
        
        double weeklyMinutes = (strengthSeconds / 60.0) / weeks;

        return buildResult(
                AnalyticsMetricType.STRENGTH_WORKLOAD,
                weeklyMinutes,
                WORKLOAD_VALUES,
                "%.0f".formatted(weeklyMinutes),
                lang,
                90.0, 150.0
        );
    }

    private MetricResult calcFrequency(List<Activity> activities, double weeks, String lang) {
		// Count sessions that qualify as strength work
		long validSessions = activities.stream()
				.count();
		double sessionsPerWeek = validSessions / weeks;

        return buildResult(
                AnalyticsMetricType.GROWTH_FREQUENCY,
                sessionsPerWeek,
                FREQ_VALUES,
                "%.1f".formatted(sessionsPerWeek),
                lang,
                2.5, 3.5
        );
    }

    private MetricResult calcAnabolicSleep(List<DailyMetric> metrics, String lang) {
        long totalDays = Math.max(1, metrics.size());
        long goodDays = metrics.stream()
                .filter(d -> d.getSleepHours() != null && d.getSleepHours() >= MIN_SLEEP_HOURS)
                .count();
        
        double nightsPerWeek = ((double) goodDays / totalDays) * DAYS_IN_WEEK;

        return buildResult(
                AnalyticsMetricType.ANABOLIC_SLEEP,
                nightsPerWeek,
                SLEEP_VALUES,
                "%.1f".formatted(nightsPerWeek),
                lang,
                4.0, 5.0
        );
    }

    /**
     * Smart Filter: Identifies Strength Training.
     * Criteria:
     * 1. Explicit Strength Type (WeightTraining, CrossFit, etc.) - TRUST THIS FIRST.
     * 2. OR Pulse Profile (High Peak + Moderate Avg) - for generic "Workout" types or unclassified.
     * 3. EXCLUDE Cardio types (Run, Ride) unless they are explicitly marked as CrossFit/HIIT (which is handled by step 1 list).
     */
    private boolean isStrengthSession(Activity a) {
        if (a.getType() == null) return false;
        
        // 1. Whitelist Check (Explicit Strength)
        boolean isExplicitStrength = STRENGTH_TYPES.stream()
                .anyMatch(type -> type.equalsIgnoreCase(a.getType()));
        
        if (isExplicitStrength) return true;

        // 2. Blacklist Check (Cardio)
        // If it's explicitly cardio and NOT in strength list, reject it.
        boolean isCardio = CARDIO_TYPES.stream()
                .anyMatch(type -> type.equalsIgnoreCase(a.getType()));
        
        if (isCardio) return false;

        // 3. Fallback: Pulse Profile Check
        // For generic types like "Other" or "Activity"
        if (a.getMaxPulse() != null && a.getAvgPulse() != null) {
            boolean hasIntensity = a.getMaxPulse() >= MIN_MAX_PULSE_PEAK; // e.g. > 130
            boolean hasDensity = a.getAvgPulse() >= MIN_AVG_PULSE_FLOOR;  // e.g. > 100 
            return hasIntensity && hasDensity;
        }

        return false;
    }
    
}