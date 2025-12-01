package com.ua.pohribnyi.fitadvisorbot.enums;

/**
 * Enumeration of all supported advanced analytics metrics. Keys are used for
 * localization in messages.yml.
 */
public enum AnalyticsMetricType {

	// Base metrics
	REGULARITY_INDEX, // Consistency of workouts
	RECOVERY_BALANCE, // Sleep vs Stress balance
	LOAD_CONSISTENCY, // Daily activity variance

	// Weight Loss Strategy
	AVG_DAILY_BURN, ACTIVE_DAYS_WEEKLY, PEAK_STEPS_DAY, TIME_TO_BURN_1KG,

	// Running Strategy
	RUNNING_CAPACITY, HEART_COMFORT, PACE_STABILITY, ENDURANCE_RESERVE, WEEKLY_VOLUME, RACE_PREDICTOR,

	// Muscle Strategy
	STRENGTH_WORKLOAD, GROWTH_FREQUENCY, ANABOLIC_SLEEP, HYPERTROPHY_BLOCK_LEFT,

	// General Health Strategy
	ACTIVE_LOAD, ZEN_DAY, RESTORATIVE_NIGHTS, ENERGY_STREAK
}