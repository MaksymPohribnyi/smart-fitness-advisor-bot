package com.ua.pohribnyi.fitadvisorbot.service.analytics;

import org.springframework.stereotype.Service;

import com.ua.pohribnyi.fitadvisorbot.model.entity.user.UserProfile;

/**
 * Domain service for physiological calculations (Heart Rate Zones, BMI, etc).
 */
@Service
public class UserPhysiologyService {

	public static final int BASE_DAILY_STEPS = 5500;
	public static final int ACTIVITY_BONUS_STEPS = 1500;
	
	public static final double KCAL_IN_KG_FAT = 7000.0;
	public static final double KCAL_PER_STEP = 0.05; // Average physiology factor

	// Cadence constants (steps per minute)
	private static final int RUN_CADENCE = 165;
	private static final int WALK_CADENCE = 110;

	// Metabolic Equivalents for conversion (approximate steps per min of effort)
	private static final int GYM_STEPS_EQ = 80; // Strength training isn't step-heavy but is load-heavy
	private static final int RIDE_STEPS_EQ = 130; // Cycling effort conversion
	private static final int DEFAULT_EQ = 100;

	/**
	 * Calculates Zone 2 (Fat Burn Zone) boundaries based on Karvonen or Simple Max
	 * HR formula. For MVP we use: (220 - age) * 0.6 to 0.75
	 */
	public HeartRateZone calculateFatBurnZone(UserProfile profile) {
		int age = profile.getAge();
		int maxHr = 220 - age;

		int minPulse = (int) (maxHr * 0.60); // 60%
		int maxPulse = (int) (maxHr * 0.75); // 75%

		return new HeartRateZone(minPulse, maxPulse);
	}

	public record HeartRateZone(int min, int max) {
		public boolean contains(Integer pulse) {
			return pulse != null && pulse >= min && pulse <= max;
		}
	}
	
	public static double getKcalInKgFat() {
		return KCAL_IN_KG_FAT;
	}

	public static double getKcalPerStep() {
		return KCAL_PER_STEP;
	}

	public int calculateSteps(String type, int durationMinutes, int rpe) {
		if (type == null)
			return 0;

		int baseSteps;

		switch (type) {
		case "Run" -> baseSteps = durationMinutes * RUN_CADENCE;
		case "Walk" -> baseSteps = durationMinutes * WALK_CADENCE;
		case "Ride" -> baseSteps = durationMinutes * RIDE_STEPS_EQ;
		case "Workout", "Gym" -> baseSteps = durationMinutes * GYM_STEPS_EQ;
		default -> baseSteps = durationMinutes * DEFAULT_EQ;
		}

		// Intensity Multiplier: Harder workout = "more steps" logic for Load Analysis
		// RPE 5 is baseline (1.0). RPE 8 = 1.3x load.
		if (rpe > 0) {
			double intensityFactor = 1.0 + ((rpe - 5) * 0.1);
			// Clamp factor to avoid crazy numbers (e.g. 0.5 to 1.5)
			intensityFactor = Math.max(0.8, Math.min(1.5, intensityFactor));
			return (int) (baseSteps * intensityFactor);
		}

		return baseSteps;
	}
	
}
