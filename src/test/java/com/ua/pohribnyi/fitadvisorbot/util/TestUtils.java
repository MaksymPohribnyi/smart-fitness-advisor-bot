package com.ua.pohribnyi.fitadvisorbot.util;

public class TestUtils {

	public static String createValidJson() {
		return """
				{
				  "dailyMetrics": [
				    {"date": "2023-10-01", "sleepHours": 7.5, "dailyBaseSteps": 5000, "stressLevel": 2}
				  ],
				  "activities": [
				    {
				      "dateTime": "2023-10-01T10:00:00",
				      "type": "Run",
				      "durationSeconds": 1800,
				      "distanceMeters": 5000,
				      "avgPulse": 140,
				      "maxPulse": 160,
				      "minPulse": 100,
				      "caloriesBurned": 300,
				      "activitySteps": 4000
				    }
				  ]
				}
				""";
	}

}
