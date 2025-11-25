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
public class RunningStrategy implements GoalAnalyticsStrategy {

	private final MessageService messageService;

	@Override
	public boolean supports(String goalCode) {
		return "run_10k".equalsIgnoreCase(goalCode) || "run_5k".equalsIgnoreCase(goalCode);
	}

	@Override
	public String getGoalTitleKey() {
		return "onboarding.goal.run_10k";
	}

	@Override
	public List<MetricResult> calculateMetrics(User user,  UserProfile profile, List<Activity> activities, List<DailyMetric> dailyMetrics) {

		List<MetricResult> results = new ArrayList<>();
		String lang = user.getLanguageCode();

		List<Activity> runs = activities.stream().filter(a -> "Run".equalsIgnoreCase(a.getType())).toList();

		// 1. Aerobic Efficiency Score (Distance / AvgPulse)
		double totalDistMeters = runs.stream().mapToDouble(Activity::getDistanceMeters).sum();
		double avgPulseSum = runs.stream().mapToDouble(a -> a.getAvgPulse() != null ? a.getAvgPulse() : 0).sum();
		double efficiency = MathUtils.safeDivide(totalDistMeters / 1000.0, avgPulseSum / Math.max(runs.size(), 1)); // km
																													// per
																													// heartbeat
																													// avg

		// Heuristic: > 0.05 is good for beginners
		String effEmoji = efficiency > 0.05 ? getStatusEmoji("good", lang) : getStatusEmoji("avg", lang);

		results.add(MetricResult.builder().type(AnalyticsMetricType.AEROBIC_EFFICIENCY)
				.formattedValue(String.format("%.2f", efficiency * 100)) // Scale up for readability
				.statusEmoji(effEmoji).build());

		// 2. Pace Stability Index (1 - CV of Pace)
		List<Double> paces = runs.stream()
				.map(a -> MathUtils.safeDivide(a.getDurationSeconds(), a.getDistanceMeters() / 1000.0)) // sec/km
				.filter(p -> p > 0).toList();
		double cvPace = MathUtils.calculateCV(paces);
		double stabilityIndex = Math.max(0, 1.0 - cvPace) * 100;

		results.add(MetricResult.builder().type(AnalyticsMetricType.PACE_STABILITY)
				.formattedValue(String.format("%.0f%%", stabilityIndex))
				.statusEmoji(stabilityIndex > 85 ? getStatusEmoji("good", lang) : getStatusEmoji("bad", lang)).build());

		// 3. Endurance Reserve (MaxHR - AvgHR)
		int maxHrAge = 220 - (profile.getAge() != null ? profile.getAge() : 30);
		double avgRunPulse = runs.stream().mapToDouble(a -> a.getAvgPulse() != null ? a.getAvgPulse() : 0).average()
				.orElse(0);

		int reserve = (int) (maxHrAge - avgRunPulse);

		results.add(MetricResult.builder().type(AnalyticsMetricType.ENDURANCE_RESERVE).formattedValue(reserve + " bpm")
				.statusEmoji(reserve > 30 ? getStatusEmoji("good", lang) : getStatusEmoji("avg", lang)).build());

		return results;
	}

	public String getExpertPraiseKey(double stability) {
		return stability > 85 ? "analytics.expert.praise.consistency" : "analytics.expert.praise.start";
	}

	public String getExpertActionKey(int reserve) {
		return reserve > 40 ? "analytics.expert.action.progression" : "analytics.expert.action.zone2";
	}

	private String getStatusEmoji(String status, String lang) {
		return messageService.getMessage("analytics.status." + status, lang);
	}

}