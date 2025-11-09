package com.ua.pohribnyi.fitadvisorbot.model.dto.strava;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;

public record StravaActivityDto(
		Long id, 
		String name, 
		String type,
		@JsonProperty("start_date_local") LocalDateTime startDateLocal,
		@JsonProperty("utc_start_time") LocalDateTime utcStartTime, 
		Double distance,
		@JsonProperty("moving_time") Integer movingTime,
		@JsonProperty("elapsed_time") Integer elapsedTime,
		@JsonProperty("total_elevation_gain") Float elevationGain, 
		@JsonProperty("elev_high") Float elevHigh,
		@JsonProperty("elev_low") Float elevLow,
		@JsonProperty("average_speed") Float averageSpeed,
		@JsonProperty("max_speed") Float maxSpeed, 
		@JsonProperty("average_heartrate") Float averageHeartrate,
		@JsonProperty("max_heartrate") Float maxHeartrate,
		@JsonProperty("average_cadence") Float averageCadence,
		@JsonProperty("average_watts") Float averageWatts, 
		Integer calories, 
		String description,
		@JsonProperty("device_name") String deviceName, 
		Boolean commute, 
		Boolean manual, 
		Boolean private_,
		String visibility, 
		@JsonProperty("flagged") Boolean flagged, 
		Integer kilojoules) {
}
