package com.ua.pohribnyi.fitadvisorbot.model.dto.analytics;

import java.util.List;

import com.ua.pohribnyi.fitadvisorbot.model.enums.AnalyticsMetricType;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PeriodReportDto {

	private String periodName;
	private String goalName; // e.g., "Weight Loss" (localized key)

	// Base Stats
	private int totalActivities;
	private double totalDistanceKm;
	private double totalDurationHours;

	// Discipline Score (0-100)
	private int consistencyScore;
	private String consistencyVerdictKey; // Key for i18n

	// Dynamic List of Advanced Metrics
	private List<MetricResult> advancedMetrics;

	// Key textual insight (generated logic)
	private String keyInsightKey;

	@Data
	@Builder
	public static class MetricResult {
		private AnalyticsMetricType type;
		private String formattedValue;
		private String statusEmoji; 
	}
}
