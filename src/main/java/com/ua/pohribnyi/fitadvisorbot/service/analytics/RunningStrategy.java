package com.ua.pohribnyi.fitadvisorbot.service.analytics;

import java.time.Duration;
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

@Component
public class RunningStrategy extends AbstractGoalStrategy {
	
	public RunningStrategy(MessageService messageService) {
		super(messageService);
	}

	// 1. Running Capacity (km in aerobic zone)
    private static final List<MetricThreshold> CAPACITY_VALUES = List.of(
            new MetricThreshold(0.0, "analytics.running.capacity.start"),    // < 3 km
            new MetricThreshold(3.0, "analytics.running.capacity.base"),     // 3-7 km
            new MetricThreshold(7.0, "analytics.running.capacity.ready"),    // 7-9 km
            new MetricThreshold(9.0, "analytics.running.capacity.hero")      // > 9 km
    );

    // 2. Pace Stability (0-100%)
    private static final List<MetricThreshold> PACE_VALUES = List.of(
            new MetricThreshold(0.0, "analytics.running.pace.chaotic"),
            new MetricThreshold(80.0, "analytics.running.pace.stable"),
            new MetricThreshold(92.0, "analytics.running.pace.metronome")
    );

    // 3. Endurance Reserve (bpm)
    private static final List<MetricThreshold> RESERVE_VALUES = List.of(
            new MetricThreshold(0.0, "analytics.running.reserve.exhausted"),
            new MetricThreshold(20.0, "analytics.running.reserve.moderate"),
            new MetricThreshold(35.0, "analytics.running.reserve.high")
    );

	// 4. Heart Comfort (% of Max HR)
	// Lower is generally better for "Base Building" (Comfort), but too low means no effort.
	// We frame it as "Zone efficiency".
	private static final List<MetricThreshold> COMFORT_VALUES = List.of(
			new MetricThreshold(0.0, "analytics.running.comfort.easy"), // < 70%
			new MetricThreshold(70.0, "analytics.running.comfort.moderate"), // 70-85%
			new MetricThreshold(85.0, "analytics.running.comfort.hard") // > 85%
	);
	
	// 5. Weekly Volume (km/week)
    private static final List<MetricThreshold> VOLUME_VALUES = List.of(
            new MetricThreshold(0.0, "analytics.running.volume.start"),   // < 10 km
            new MetricThreshold(10.0, "analytics.running.volume.base"),   // 10-20 km
            new MetricThreshold(20.0, "analytics.running.volume.solid"),  // 20-30 km
            new MetricThreshold(30.0, "analytics.running.volume.pro")     // > 30 km
    );
    
	// 6. Race Predictor (Minutes)
	private static final List<MetricThreshold> PREDICT_VALUES = List.of(
			new MetricThreshold(0.0, "analytics.running.predict.elite"),
			new MetricThreshold(45.0, "analytics.running.predict.fast"),
			new MetricThreshold(55.0, "analytics.running.predict.avg"),
			new MetricThreshold(65.0, "analytics.running.predict.start"));

	@Override
	public boolean supports(String goalCode) {
		return "run_10k".equalsIgnoreCase(goalCode) || "run_5k".equalsIgnoreCase(goalCode);
	}

	@Override
	public String getGoalTitleKey() {
		return "onboarding.goal.run_10k";
	}

	@Override
	public List<MetricResult> calculateMetrics(User user, UserProfile profile, List<Activity> activities,
			List<DailyMetric> dailyMetrics, Duration duration) {

		List<MetricResult> results = new ArrayList<>();
		String lang = user.getLanguageCode();

		List<Activity> runs = activities.stream().
				filter(a -> "Run".equalsIgnoreCase(a.getType()))
				.toList();

		if (runs.isEmpty()) return results;
		
		// 1. Running Capacity
        results.add(calcRunningCapacity(runs, profile, lang));

        // 2. Aerobic Efficiency (NEW)
        results.add(calcHeartComfort(runs, profile, lang));

        // 3. Pace Stability
        results.add(calcPaceStability(runs, lang));

        // 4. Endurance Reserve
        results.add(calcEnduranceReserve(runs, profile, lang));
        
        // 5. Weekly volume
		results.add(calcWeeklyVolume(runs, duration, lang));

		// 6. Race Predictor (NEW)
		results.add(calcRacePredictor(runs, lang));

		return results;
	}
	
	@Override
    public String getExpertPraiseKey(double consistencyScore, List<MetricResult> metrics) {
        if (consistencyScore >= 80) return "analytics.expert.praise.run_consistency";
        return "analytics.expert.praise.run_start";
    }

    @Override
    public String getExpertActionKey(double consistencyScore, List<MetricResult> metrics) {
        if (consistencyScore >= 80) return "analytics.expert.action.run_intervals"; 
        if (consistencyScore >= 50) return "analytics.expert.action.run_long";      
        return "analytics.expert.action.run_recovery";                              
    }

	private MetricResult calcRunningCapacity(List<Activity> runs, UserProfile profile, String lang) {
        int age = profile.getAge() != null ? profile.getAge() : 30;
        int maxHr = 220 - age;
        int aerobicLimit = (int) (maxHr * 0.85); // 85% MaxHR

        double maxAerobicDistKm = runs.stream()
				.filter(a -> a.getAvgPulse() <= aerobicLimit)
                .mapToDouble(Activity::getDistanceMeters)
                .max().orElse(0.0) / 1000.0;

        return buildResult(
                AnalyticsMetricType.RUNNING_CAPACITY,
                maxAerobicDistKm,
                CAPACITY_VALUES,
                "%.1f".formatted(maxAerobicDistKm), 
                lang,
                3.0, 7.0 // Avg > 3km, Good > 7km
        );
    }

	private MetricResult calcHeartComfort(List<Activity> runs, UserProfile profile, String lang) {
        int age = profile.getAge() != null ? profile.getAge() : 30;
        int maxHr = 220 - age;

        double avgPulse = runs.stream()
                .mapToInt(Activity::getAvgPulse)
                .filter(p -> p > 0)
                .average()
                .orElse(0);
        
        if (maxHr == 0) maxHr = 190; // Safety
        double percentMax = (avgPulse / maxHr) * 100.0;

        return buildResult(
                AnalyticsMetricType.HEART_COMFORT,
                percentMax, // Use % for text lookup
                COMFORT_VALUES,
                "%.0f".formatted(avgPulse), // Arg for template: "Moderate (145 bpm)"
                lang,
                100 - 85, // Avg threshold (corresponding to 85% HR)
                100 - 75  // Good threshold (corresponding to 75% HR)
        );
    }
	
    private MetricResult calcPaceStability(List<Activity> runs, String lang) {
        List<Double> paces = runs.stream()
                .map(a -> MathUtils.safeDivide(a.getDurationSeconds(), a.getDistanceMeters()))
                .filter(p -> p > 0)
                .toList();
        
        double cv = MathUtils.calculateCV(paces);
        double stability = Math.max(0, (1.0 - cv) * 100.0);

        return buildResult(
                AnalyticsMetricType.PACE_STABILITY,
                stability,
                PACE_VALUES,
                "%.0f".formatted(stability),
                lang,
                80.0, 92.0 // Avg > 80%, Good > 92%
        );
    }

    private MetricResult calcEnduranceReserve(List<Activity> runs, UserProfile profile, String lang) {
        int age = profile.getAge() != null ? profile.getAge() : 30;
        int maxHr = 220 - age;
        
        double avgRunPulse = runs.stream()
                .mapToInt(a -> (a.getAvgPulse() != null) ? a.getAvgPulse() : 0)
                .filter(p -> p > 0)
                .average().orElse(150.0);
        
        double reserve = Math.max(0, maxHr - avgRunPulse);

        return buildResult(
                AnalyticsMetricType.ENDURANCE_RESERVE,
                reserve,
                RESERVE_VALUES,
                "%.0f".formatted(reserve),
                lang,
                20.0, 35.0 // Avg > 20 bpm, Good > 35 bpm
        );
    }
    
    private MetricResult calcWeeklyVolume(List<Activity> runs, Duration duration, String lang) {
        double totalDistKm = runs.stream()
        		.mapToDouble(Activity::getDistanceMeters)
        		.sum() / 1000.0;

        long days = Math.max(1, duration.toDays());
        double weeks = days / 7.0;
        double weeklyAvg = MathUtils.safeDivide(totalDistKm, weeks);
        
        return buildResult(
                AnalyticsMetricType.WEEKLY_VOLUME,
                weeklyAvg,
                VOLUME_VALUES,
                "%.1f".formatted(weeklyAvg),
                lang,
                10.0, 20.0
        );
    }
    
    private MetricResult calcRacePredictor(List<Activity> runs, String lang) {
    	double avgPaceSec = runs.stream()
				.filter(a -> a.getDistanceMeters() > 0)
				.mapToDouble(a -> (double) a.getDurationSeconds() / (a.getDistanceMeters() / 1000.0))
				.average().orElse(360.0); // 6:00 min/km default
		
		double racePace = Math.max(180, avgPaceSec - 30); 
		double predictedTimeMinutes = (racePace * 10) / 60.0; // 10 km time

		return buildResult(
				AnalyticsMetricType.RACE_PREDICTOR,
				predictedTimeMinutes,
				PREDICT_VALUES,
				"%.0f".formatted(predictedTimeMinutes),
				lang,
				0.0, 1000.0 // Always green for motivation
		);
	}

}