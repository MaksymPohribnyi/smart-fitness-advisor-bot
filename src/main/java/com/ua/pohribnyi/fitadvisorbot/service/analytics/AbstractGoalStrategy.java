package com.ua.pohribnyi.fitadvisorbot.service.analytics;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.ua.pohribnyi.fitadvisorbot.model.dto.analytics.PeriodReportDto.MetricResult;
import com.ua.pohribnyi.fitadvisorbot.model.entity.Activity;
import com.ua.pohribnyi.fitadvisorbot.model.entity.DailyMetric;
import com.ua.pohribnyi.fitadvisorbot.model.entity.user.User;
import com.ua.pohribnyi.fitadvisorbot.model.enums.AnalyticsMetricType;
import com.ua.pohribnyi.fitadvisorbot.service.telegram.MessageService;
import com.ua.pohribnyi.fitadvisorbot.util.math.MathUtils;
import com.ua.pohribnyi.fitadvisorbot.util.math.MetricThreshold;

import lombok.RequiredArgsConstructor;

/**
 * Base class for all analytic strategies.
 * Implements calculation of universal "Base Metrics" (Regularity, Recovery, Load).
 */
@RequiredArgsConstructor
public abstract class AbstractGoalStrategy implements GoalAnalyticsStrategy {

    protected final MessageService messageService;
    private static final int ACTIVE_STEPS_THRESHOLD = 7000; // Minimum steps to count as "active" day

    // --- BASE METRICS CONFIGURATION ---
    
    // Regularity (0-100%): < 50% Bad, > 80% Excellent
    private static final List<MetricThreshold> REGULARITY_VALUES = List.of(
            new MetricThreshold(0.0, "analytics.base.regularity.low"),
            new MetricThreshold(2.5, "analytics.base.regularity.medium"),
            new MetricThreshold(3.5, "analytics.base.regularity.high")
    );

    // Recovery Balance (Index): < 0 Drained, > 3 Charged
    private static final List<MetricThreshold> RECOVERY_VALUES = List.of(
            new MetricThreshold(-5.0, "analytics.base.recovery.drained"),
            new MetricThreshold(0.0, "analytics.base.recovery.balanced"),
            new MetricThreshold(3.0, "analytics.base.recovery.charged")
    );

    // Load Consistency (0-100%): > 85% is very stable
    private static final List<MetricThreshold> LOAD_VALUES = List.of(
            new MetricThreshold(0.0, "analytics.base.load.chaos"),
            new MetricThreshold(60.0, "analytics.base.load.stable"),
            new MetricThreshold(85.0, "analytics.base.load.perfect")
    );

    /**
     * Calculates universal metrics relevant for ANY goal.
     */
    public List<MetricResult> calculateBaseMetrics(User user, List<Activity> activities, List<DailyMetric> dailyMetrics, Duration duration) {
        List<MetricResult> results = new ArrayList<>();
        String lang = user.getLanguageCode();

        // 1. Regularity Index (RI)
        long totalDays = Math.max(1, duration.toDays());
        long activeDaysCount = calculateActiveDays(activities, dailyMetrics);

		double activeDaysPerWeek = ((double) activeDaysCount / totalDays) * 7.0;

        results.add(buildResult(
                AnalyticsMetricType.REGULARITY_INDEX,
                activeDaysPerWeek, 
                REGULARITY_VALUES,
                "%.1f".formatted(activeDaysPerWeek), 
                lang, 2.5, 4.0
        ));

        // 2. Recovery Balance (RB)
        // Logic: AvgSleep - AvgStress. Example: 7.5h - 2.5 = 5.0 (Great). 6.0h - 4.0 = 2.0 (Poor).
        double avgSleep = dailyMetrics.stream().mapToDouble(DailyMetric::getSleepHours).average().orElse(7.0);
        double avgStress = dailyMetrics.stream().mapToDouble(DailyMetric::getStressLevel).average().orElse(3.0);
        double rbScore = avgSleep - avgStress;

        results.add(buildResult(
                AnalyticsMetricType.RECOVERY_BALANCE,
                rbScore,
                RECOVERY_VALUES,
               "%.1f".formatted(rbScore),
                lang,
                0.0, 3.0
        ));

        // 3. Load Consistency (LS)
        // Logic: Stability of daily steps/load. 100 - Coefficient of Variation (CV).
        List<Double> dailyLoads = dailyMetrics.stream()
                .map(d -> (double) d.getDailyBaseSteps())
                .toList();
        
        double cv = MathUtils.calculateCV(dailyLoads); // e.g. 0.15 (15% variance)
        double consistency = Math.max(0, (1.0 - cv) * 100);

        results.add(buildResult(
                AnalyticsMetricType.LOAD_CONSISTENCY,
                consistency,
                LOAD_VALUES,
                "%.1f".formatted(consistency),
                lang,
                60.0, 85.0
        ));

        return results;
    }

    /**
     * Helper to build a standardized MetricResult.
     */
    protected MetricResult buildResult(AnalyticsMetricType type, double value, List<MetricThreshold> rules,
            String formattedNumb, String lang, double avgThreshold, double goodThreshold) {
        
        String valueKey = MetricThreshold.pick(value, rules);
        String valueText = messageService.getMessage(valueKey, lang, formattedNumb);
        
        // Determine emoji based on numeric thresholds
        String statusKey = value >= goodThreshold ? "analytics.status.good"
                : value >= avgThreshold ? "analytics.status.avg" : "analytics.status.bad";
        String emoji = messageService.getMessage(statusKey, lang);

        return MetricResult.builder()
                .type(type)
                .formattedValue(valueText)
                .statusEmoji(emoji)
                .build();
    }
    
    private long calculateActiveDays(List<Activity> activities, List<DailyMetric> dailyMetrics) {
        Set<LocalDate> activeDates = new HashSet<>();

        activities.forEach(a -> activeDates.add(a.getDateTime().toLocalDate()));

        dailyMetrics.stream()
                .filter(d -> d.getDailyBaseSteps() != null && d.getDailyBaseSteps() >= ACTIVE_STEPS_THRESHOLD)
                .forEach(d -> activeDates.add(d.getDate()));

        return activeDates.size();
    }
}
