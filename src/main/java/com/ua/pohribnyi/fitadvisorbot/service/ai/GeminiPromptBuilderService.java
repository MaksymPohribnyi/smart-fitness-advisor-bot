package com.ua.pohribnyi.fitadvisorbot.service.ai;

import java.time.LocalDate;

import org.springframework.stereotype.Service;

import com.ua.pohribnyi.fitadvisorbot.model.entity.user.UserProfile;

@Service
public class GeminiPromptBuilderService {

	private static final String PROMPT_TEMPLATE = """
			You are a fitness data simulator. Generate ONLY valid JSON - no text before or after.

			USER PROFILE:
			- Activity Level: %s
			- Goal: %s

			TASK: Generate 60-day fitness history ending on %s.

			STRICT OUTPUT FORMAT:
			{
			  "dailyMetrics": [
			    {
			      "date": "YYYY-MM-DD",
			      "sleepHours": 5.0-9.0,
			      "dailyBaseSteps": 3000-8000,
			      "stressLevel": 1-5
			    }
			    // ... exactly 60 entries
			  ],
			  "activities": [
			    {
			      "dateTime": "YYYY-MM-DDTHH:MM:SS",
			      "type": "Run",
			      "durationSeconds": 1200-5400,
			      "distanceMeters": 3000-15000,
			      "avgPulse": 130-175,
			      "maxPulse": avgPulse + 15-30,
			      "minPulse": avgPulse - 20-40,
			      "caloriesBurned": 200-800,
			      "activitySteps": 3000-15000
			    }
			    // ... 15-25 activities spread over 60 days
			  ]
			}

			CRITICAL RULES:
			1. CORRELATIONS:
			   - Poor sleep (< 6.5h) → Higher avgPulse (+8-12 bpm) + Lower performance
			   - High stress (4-5) → Fewer activities that week
			   - Good sleep (> 7.5h) → Better pace, lower resting heart rate

			2. PROGRESSION (Goal: %s):
			   - Week 1-2: Baseline performance
			   - Week 3-5: Gradual improvement (5-10%%)
			   - Week 6-7: Minor setback (simulate reality)
			   - Week 8-9: Strong finish (10-15%% better than baseline)

			3. REALISM:
			   - 2-4 activities per week (not daily)
			   - Weekend activities often longer
			   - Include 1-2 "bad weeks" (low sleep, high stress, poor workouts)
			   - Pulse variations: ±5 bpm day-to-day for same effort

			4. DATE CONSISTENCY:
			   - The last entry MUST be on %s (today).
               - Go backwards exactly 60 days.
               - Activities must match dailyMetrics dates.
			   - No future dates

			5. VALUE CONSTRAINTS:
			   - All numbers must be within specified ranges
			   - No null values
			   - maxPulse > avgPulse > minPulse
			   - durationSeconds and distanceMeters must correlate (reasonable pace)

			VALIDATION CHECKLIST:
			✓ Exactly 60 dailyMetrics entries
			✓ 15-25 activities
			✓ All dates in YYYY-MM-DD or YYYY-MM-DDTHH:MM:SS format
			✓ No markdown formatting (no ```json)
			✓ Valid JSON syntax

			Output ONLY the JSON object. No explanations.
			""";

	public String buildOnboardingPrompt(UserProfile profile) {
		String levelDescription = profile.getLevel();
		String goalDescription = profile.getGoal();
		LocalDate today = LocalDate.now();

		return String.format(PROMPT_TEMPLATE, levelDescription, goalDescription, today, goalDescription, today);
	}
}