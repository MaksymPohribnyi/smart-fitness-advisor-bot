package com.ua.pohribnyi.fitadvisorbot.model.dto.analytics;

import java.util.List;

import com.ua.pohribnyi.fitadvisorbot.enums.AnalyticsMetricType;

import lombok.Builder;
import lombok.Data;
@Data

@Builder
public class PeriodReportDto {

	private String periodKey;
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
	private List<MetricResult> baseMetrics; 

	// SPECIAL: Prediction Metric (Separated from advanced metrics for UI emphasis)
    private MetricResult predictionMetric;

    // Smart Advisor Summary (Single behavioral frame key)
    private String advisorSummaryKey;

	@Data
	@Builder
	public static class MetricResult {
		private AnalyticsMetricType type;
		private String formattedValue;
		private Double rawValue;
		private String statusEmoji; 
	}
}
