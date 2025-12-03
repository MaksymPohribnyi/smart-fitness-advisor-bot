package com.ua.pohribnyi.fitadvisorbot.service.ai.prompt;

import java.time.LocalDate;
import java.util.Locale;

import org.springframework.stereotype.Service;

import com.ua.pohribnyi.fitadvisorbot.model.entity.user.UserProfile;

@Service
public class GeminiPromptBuilderService {

	private static final String PROMPT_TEMPLATE = """
			You are a fitness data simulator. Generate realistic 30-day history.

			PROFILE: %s level, Goal: %s, End date: %s

			REQUIREMENTS:
			1. Exactly 28-30 dailyMetrics (one per day, sequential backwards from %s)
			2. Exactly %d-%d activities total (for entire month)
			3. Activity types: %s

			RANGES (vary naturally, not constant values):
			- Sleep: %.1f-%.1f hours
			- DailyBaseSteps: %d-%d (excluding workout steps)
			- Stress: %d-%d (1=low, 5=high)

			ACTIVITY RULES (for %s level):
			%s

			STEP CALCULATION:
			- Running: steps = distanceMeters / 1.3 (range: distance/1.4 to distance/1.2)
			- Walking: steps = distanceMeters / 0.75 (range: distance/0.8 to distance/0.7)
			- Cycling/Workout: steps = 0

			CORRELATIONS:
			- Sleep <6h → next day: stress+1, avgPulse+10
			- Hard workout (pulse>165 or >50min) → next day: light/rest
			- Stress 4-5 → reduce steps by 30%%
			- Include 2-3 "bad days" (poor sleep, low activity, high stress)

			VALIDATION:
			- maxPulse = avgPulse + (15 to 35)
			- minPulse = avgPulse - (20 to 40)
			- Running pace: 4:30-10:00 min/km | Cycling: 14-35 km/h
			- NO 3 consecutive high-intensity days

			Output ONLY valid JSON.
			""";

	public String buildOnboardingPrompt(UserProfile profile) {
		String level = profile.getLevel() != null ? profile.getLevel() : "beginner";
		String goal = profile.getGoal();
		LocalDate today = LocalDate.now();

		GenerationConfig config = GenerationConfig.forLevel(level, goal);

		int minActivities = switch (level.toLowerCase()) {
		case "pro", "advanced" -> 14;
		case "moderate", "intermediate" -> 10;
		default -> 8;
		};

		int maxActivities = minActivities + 4;

        return String.format(Locale.US, PROMPT_TEMPLATE,
                // Context
                level, goal, today,
                today,
                minActivities, maxActivities,
                config.activityDistribution,
                // Dynamic Constraints
                config.minSleep, config.maxSleep,
                config.minSteps, config.maxSteps,
                config.minStress, config.maxStress,
                level,
                config.activityPatterns
        );
	}
	
	private record GenerationConfig(double minSleep, double maxSleep, int minSteps, int maxSteps, int minStress,
			int maxStress, String activityPatterns, String activityDistribution) {
		
		static GenerationConfig forLevel(String level, String goal) {
			String distribution = getDistributionForGoal(goal);

			return switch (level.toLowerCase()) {
			case "pro", "advanced" -> new GenerationConfig(
					7.0, 8.0, 
					10000, 14000, 
					1, 3, 
					"""
					Run: 4:30-5:30 min/km, 35-60min, 7-11km, pulse 150-170 (max 165-185)
					Cycle: 22-30 km/h, 45-75min, 18-35km, pulse 135-155 (max 150-170)
					Workout: 30-50min, strength/HIIT, pulse 125-150 (max 140-165)
					Walk: 20-35min, 2-4km, pulse 95-110 (recovery only)
					""",
					distribution);
			case "moderate", "intermediate" -> new GenerationConfig(
					6.0, 8.0, 
					6000, 9000, 
					2, 4, 
					"""
					Run: 5:45-7:15 min/km, 25-45min, 4-6.5km, pulse 140-160 (max 155-175)
					Cycle: 18-24 km/h, 40-60min, 12-22km, pulse 130-150 (max 145-165)
					Workout: 25-45min, bodyweight/light weights, pulse 120-145 (max 135-160)
					Walk: 20-40min, 2-4km, pulse 90-105
					""",
					distribution);
			default -> new GenerationConfig( // Beginner
					5.0, 7.0, 
					2500, 6500, 
					3, 5, 
					"""
					Run: 7:30-9:30 min/km, 20-35min, 2.5-4km, pulse 130-150 (max 145-165)
					Walk: 12-16 min/km, 20-45min, 2-4km, pulse 90-110 (max 100-120)
					Cycle: 14-19 km/h, 30-50min, 8-14km, pulse 115-135 (max 130-150)
					Workout: 15-30min, yoga/stretching, pulse 100-125 (max 115-140)
					""", 
					distribution);
			};
		}

		private static String getDistributionForGoal(String goal) {
			if (goal == null)
				goal = "";
			return switch (goal.toLowerCase()) {
			case "run_10k", "run 10k", "пробігти 10 км" -> "Run 75-85% (PRIORITY), Walk 5-15%, Workout 5-10%";
			case "lose_weight", "схуднути" -> "Run 20-30%, Walk 45-55%, Workout 25-30%";
			case "build_muscle", "набрати м'язи" -> "Workout 65-75% (PRIORITY), Run 10-15%, Walk 10-20%";
			default -> "Walk 50-60%, Run 20-30%, Workout 15-25%";
			};
		}
	
	}
	
	
}