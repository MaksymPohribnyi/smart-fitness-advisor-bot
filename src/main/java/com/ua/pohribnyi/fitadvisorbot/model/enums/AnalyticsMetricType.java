package com.ua.pohribnyi.fitadvisorbot.model.enums;

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
	FAT_FUEL_CONSISTENCY, DEFICIT_STABILITY, METABOLIC_MOMENTUM, RECOVERY_TO_BURN,

	// Running Strategy
	RUNNING_CAPACITY, HEART_COMFORT, PACE_STABILITY, ENDURANCE_RESERVE, WEEKLY_VOLUME, RACE_PREDICTOR,

	// Muscle Strategy
	HYPERTROPHY_MINUTES, STRENGTH_RECOVERY_ALIGNMENT, PROGRESSIVE_LOAD, OVERLOAD_READINESS,

	// General Health Strategy
	HEART_HEALTH_SAVINGS, BALANCED_DAY_RATIO, VITALITY_COHERENCE, MOVEMENT_SUFFICIENCY
}