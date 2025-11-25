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

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class HealthStrategy implements GoalAnalyticsStrategy {

    private final UserPhysiologyService physiologyService;
    private final MessageService messageService;

    @Override
    public boolean supports(String goalCode) {
        return "health".equalsIgnoreCase(goalCode);
    }

    @Override
    public String getGoalTitleKey() {
        return "onboarding.goal.health";
    }

    @Override
    public List<MetricResult> calculateMetrics(User user, UserProfile profile, List<Activity> activities, List<DailyMetric> dailyMetrics) {
        
    	List<MetricResult> results = new ArrayList<>();
        String lang = user.getLanguageCode();
        
        // 1. Heart Health Savings (Zone 2 minutes * 3)
        var z2 = physiologyService.calculateFatBurnZone(profile);
        long z2Seconds = activities.stream()
                .filter(a -> z2.contains(a.getAvgPulse()))
                .mapToLong(Activity::getDurationSeconds)
                .sum();
        long savingsMinutes = (z2Seconds / 60) * 3;
        
        results.add(MetricResult.builder()
                .type(AnalyticsMetricType.HEART_HEALTH_SAVINGS)
                .formattedValue("+" + savingsMinutes + " хв")
                .statusEmoji(getStatusEmoji("good", lang))
                .build());

        // 2. Balanced Day Ratio (Sleep > 7 AND Stress < 4)
        long balancedDays = dailyMetrics.stream()
                .filter(d -> d.getSleepHours() >= 7.0 && d.getStressLevel() < 4)
                .count();
        long totalDays = Math.max(dailyMetrics.size(), 1);
        int ratioPct = (int)((double)balancedDays / totalDays * 100);

        results.add(MetricResult.builder()
                .type(AnalyticsMetricType.BALANCED_DAY_RATIO)
                .formattedValue(ratioPct + "%")
                .statusEmoji(ratioPct > 50 ? getStatusEmoji("good", lang) : getStatusEmoji("avg", lang))
                .build());

        // 3. Vitality Coherence (Dummy for MVP - based on stress stability)
        results.add(MetricResult.builder()
                .type(AnalyticsMetricType.VITALITY_COHERENCE)
                .formattedValue("Висока")
                .statusEmoji(getStatusEmoji("good", lang))
                .build());

        return results;
    }
    
    private String getStatusEmoji(String status, String lang) {
        return messageService.getMessage("analytics.status." + status, lang);
    }
}