package com.ua.pohribnyi.fitadvisorbot.service.ai.prompt;

import java.time.LocalDate;
import java.util.Locale;

import org.springframework.stereotype.Service;

import com.ua.pohribnyi.fitadvisorbot.model.entity.user.UserProfile;

@Service
public class GeminiPromptBuilderService {

	private static final String PROMPT_TEMPLATE = """
			You are an advanced physiology simulator. 
            Task: Generate realistic fitness data representing a HUMAN, not a robot.

            USER PROFILE:
            - Activity Level: %s
            - Goal: %s
            - Target: 30 days fitness history ending on %s.

			SIMULATION PARAMETERS (Based on Level):
            1. SLEEP Baseline: %.1f - %.1f hours
            2. STEPS Baseline: %d - %d steps/day
            3. STRESS Baseline: %d - %d (1=Low, 5=High)
            4. WORKOUTS:
               - Frequency: %d - %d sessions/week
               - Duration: %d - %d minutes
          
			ACTIVITY PATTERNS (Level-Specific Physics):
               %s 

			CRITICAL HUMAN RULES (Apply strict correlations):

            1. HEART RATE LOGIC:
               - Running: Avg Pulse 135-175. Max: avgPulse + 15-30. Min: avgPulse - 20-40
               - Walking: Avg Pulse 90-110.
			
			2. SLEEP IMPACT:
			   - If sleep < 6.0h → Next day stress +1 → Higher avgPulse (+8-12 bpm) + Lower performance
			   - If sleep < 5.0h → Skip workout or very light activity only
			   - Good sleep (> 7.0h) → Better performance (Better pace, lower resting heart rate)
			   
			3. STRESS IMPACT:
               - High stress (4-5) → Reduces daily steps (sedentary behavior).
               - High stress days should rarely have high-intensity workouts.   

			4. RECOVERY:
               - After a hard workout (high pulse/long duration), the next day MUST be lighter or rest.
               - Do not generate High Intensity workouts 3 days in a row. Interleave with Rest or Light days.

			5. PROGRESSION & REALISM:
               - Include natural variance (don't output exactly 8000 steps every day).
               - Weekends: Change patterns (e.g., longer sleep, longer walk/run).
               - "Bad Week": Simulate 3-4 days of low motivation/bad stats randomly.

			6. DATA INTEGRITY:
               - Dates must be sequential backwards from %s.
               - No future dates
               - Pace check: Running pace must be realistic (e.g., 4:45-8:00 min/km). 
            
			7. VALUE CONSTRAINTS:
			   - All numbers must be within specified ranges
			   - No null values
			   - maxPulse > avgPulse > minPulse
			   - durationSeconds and distanceMeters must correlate (reasonable pace)
			
			VALIDATION CHECKLIST:
			✓ Exactly 28-30 dailyMetrics entries
			✓ 10-18 activities

			Output ONLY valid JSON matching the Schema.
			""";

	public String buildOnboardingPrompt(UserProfile profile) {
		String level = profile.getLevel() != null ? profile.getLevel() : "beginner";
		String goal = profile.getGoal();
		LocalDate today = LocalDate.now();

		GenerationConfig config = GenerationConfig.forLevel(level);

        return String.format(Locale.US, PROMPT_TEMPLATE,
                // Context
                level, goal, today,
                // Dynamic Constraints
                config.minSleep, config.maxSleep,
                config.minSteps, config.maxSteps,
                config.minStress, config.maxStress,
                config.minFreq, config.maxFreq,
                config.minDuration, config.maxDuration,
                config.activityPatterns,
                // Validation Date
                today
        );
	}
	
	private record GenerationConfig(
            double minSleep, double maxSleep,
            int minSteps, int maxSteps,
            int minStress, int maxStress,
            int minFreq, int maxFreq,
            int minDuration, int maxDuration,
            String activityPatterns
    ) {
		static GenerationConfig forLevel(String level) {
			return switch (level.toLowerCase()) {
			case "pro", "advanced" -> new GenerationConfig(
					7.0, 9.0, // Sleep
					10000, 16000, // Steps
					1, 3, // Stress
					4, 6, // Freq
					45, 90, // Duration
					"""
                    - RUNNING: Fast Pace (4:00-5:15 min/km). 
                      Example: 40 min -> 8-10 km. 
                    - CYCLING: High Speed (25-35 km/h). 
                      Example: 60 min -> 25-35 km.
                    - WALKING: Power Walking (Active).
                    - WORKOUT: Intense. High Pulse.
                    """);
			case "moderate", "intermediate" ->
				new GenerationConfig(
						6.5, 8.0, 
						6000, 11000, 
						2, 4, 
						2, 4, 
						30, 60, 
						"""
                        - RUNNING: Moderate Pace (5:30-7:00 min/km). 
                          Example: 30 min -> 4.5-5.5 km.
                        - CYCLING: Moderate Speed (18-24 km/h). 
                          Example: 60 min -> 18-24 km.
                        - WALKING: Normal Pace.
                        - WORKOUT: Moderate. 
                        """);
			default -> new GenerationConfig( // Beginner
					5.0, 7.0, 
					2500, 6500, 
					3, 5, 
					1, 2, 
					15, 35, 
					"""
                    - RUNNING: Slow Jog / Walk-Run (7:30-10:00 min/km). 
                      Example: 30 min -> 3.0-4.0 km.
                    - CYCLING: Leisure Speed (14-19 km/h). 
                      Example: 60 min -> 12-16 km.
                    - WALKING: Casual Stroll (Slow).
                    - WORKOUT: Light/Yoga. Low Pulse.
                    """);
			};
		}
	}
	
	
}