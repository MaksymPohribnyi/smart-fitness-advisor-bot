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
public class MuscleBuildingStrategy implements GoalAnalyticsStrategy {

    private final MessageService messageService;

    @Override
    public boolean supports(String goalCode) {
        return "build_muscle".equalsIgnoreCase(goalCode);
    }

    @Override
    public String getGoalTitleKey() {
        return "onboarding.goal.build_muscle";
    }

    @Override
    public List<MetricResult> calculateMetrics(User user,  UserProfile profile, List<Activity> activities, List<DailyMetric> dailyMetrics) {
       
    	List<MetricResult> results = new ArrayList<>();
        String lang = user.getLanguageCode();
        
        int maxHr = 220 - (profile.getAge() != null ? profile.getAge() : 30);
        int minHyper = (int)(maxHr * 0.65);
        int maxHyper = (int)(maxHr * 0.80);

        // 1. Hypertrophy Zone Minutes
        long hyperSeconds = activities.stream()
                .filter(a -> a.getAvgPulse() != null && a.getAvgPulse() >= minHyper && a.getAvgPulse() <= maxHyper)
                .mapToLong(Activity::getDurationSeconds)
                .sum();
        int hyperMinutes = (int) (hyperSeconds / 60);
        
        results.add(MetricResult.builder()
                .type(AnalyticsMetricType.HYPERTROPHY_MINUTES)
                .formattedValue(hyperMinutes + " хв")
                .statusEmoji(hyperMinutes > 120 ? getStatusEmoji("good", lang) : getStatusEmoji("avg", lang))
                .build());

        // 2. Strength-Recovery Alignment (Correlation between activity duration and sleep)
        double avgSleep = dailyMetrics.stream().mapToDouble(DailyMetric::getSleepHours).average().orElse(0);
        String alignEmoji = avgSleep > 7.5 ? getStatusEmoji("good", lang) : getStatusEmoji("bad", lang);
        String alignVal = avgSleep > 7.5 ? "High" : "Low";

        results.add(MetricResult.builder()
                .type(AnalyticsMetricType.STRENGTH_RECOVERY_ALIGNMENT)
                .formattedValue(alignVal)
                .statusEmoji(alignEmoji)
                .build());

        // 3. Progressive Load Momentum (Duration trend)
        List<Double> durations = activities.stream().map(a -> (double)a.getDurationSeconds()).toList();
        double slope = MathUtils.calculateSlope(durations);
        
        results.add(MetricResult.builder()
                .type(AnalyticsMetricType.PROGRESSIVE_LOAD)
                .formattedValue(slope > 0 ? "↗️ Up" : "➡️ Stable")
                .statusEmoji(slope > 0 ? getStatusEmoji("good", lang) : getStatusEmoji("avg", lang))
                .build());

        return results;
    }
    
    private String getStatusEmoji(String status, String lang) {
        return messageService.getMessage("analytics.status." + status, lang);
    }
    
}