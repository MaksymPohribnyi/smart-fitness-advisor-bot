package com.ua.pohribnyi.fitadvisorbot.service.ai.schema;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.google.genai.types.Schema;
import com.google.genai.types.Type;

@Component
public class GeminiSchemaDefiner {

    public Schema getFitnessHistorySchema() {
        Schema activitySchema = Schema.builder()
                .type(Type.Known.OBJECT)
                .properties(Map.of(
                        "dateTime", Schema.builder().type(Type.Known.STRING).description("ISO8601 format: YYYY-MM-DDTHH:MM:SS").build(),
                        "type", Schema.builder().type(Type.Known.STRING).enum_(List.of("Run", "Workout", "Walk")).build(),
                        "durationSeconds", Schema.builder().type(Type.Known.INTEGER).description("Duration in seconds").build(),
                        "distanceMeters", Schema.builder().type(Type.Known.INTEGER).description("Distance in meters (0 for workouts)").build(),
                        "avgPulse", Schema.builder().type(Type.Known.INTEGER).description("Average Heart Rate (90-190)").build(),
                        "maxPulse", Schema.builder().type(Type.Known.INTEGER).description("Max Heart Rate (must be > avgPulse)").build(),
                        "minPulse", Schema.builder().type(Type.Known.INTEGER).description("Min Heart Rate (must be < avgPulse)").build(),
                        "caloriesBurned", Schema.builder().type(Type.Known.INTEGER).build(),
                        "activitySteps", Schema.builder().type(Type.Known.INTEGER).build()
                ))
                .required(List.of("dateTime", "type", "durationSeconds", "avgPulse", "maxPulse", "caloriesBurned"))
                .build();

		Schema metricSchema = Schema.builder()
				.type(Type.Known.OBJECT)
				.properties(Map.of(
						"date", Schema.builder().type(Type.Known.STRING).description("YYYY-MM-DD").build(),
						"sleepHours", Schema.builder().type(Type.Known.NUMBER).description("Float hours (e.g. 7.5)").build(),
						"dailyBaseSteps", Schema.builder().type(Type.Known.INTEGER).description("Steps excluding workouts").build(),
						"stressLevel", Schema.builder().type(Type.Known.INTEGER).description("1 to 5").build()))
				.required(List.of("date", "sleepHours", "dailyBaseSteps", "stressLevel"))
				.build();

		return Schema.builder()
				.type(Type.Known.OBJECT)
				.properties(Map.of(
						"dailyMetrics",	Schema.builder()
						.type(Type.Known.ARRAY)
						.items(metricSchema)
						.description("EXACTLY 28-30 entries, one per day")
						.build(),
						"activities", Schema.builder()
						.type(Type.Known.ARRAY)
						.items(activitySchema)
						.description("10-18 activities total for 30 days")
						.build()
						))
				.required(List.of("dailyMetrics", "activities"))
				.build();
	}
}
