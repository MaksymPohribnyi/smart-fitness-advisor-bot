package com.ua.pohribnyi.fitadvisorbot.model.dto.strava;

import com.fasterxml.jackson.annotation.JsonProperty;

public record StravaAthleteDto(
		Long id,
		@JsonProperty("resource_state") String recourceState,
		@JsonProperty("firstname")String firstName,
		@JsonProperty("lastname")String lastName, 
		String city, 
		String state, 
		String country,
		String sex,
		@JsonProperty("summit") Boolean premium,
		@JsonProperty("created_at") String createdAt,
		@JsonProperty("updated_at") String updatedAt, 
		@JsonProperty("follower_count") Integer followerCount, 
		@JsonProperty("friend_count") Integer friendCount,
		@JsonProperty("badge_type_id") Integer badgeTypeId, 
		Float weight,
		@JsonProperty("profile_medium") String profileMedium, 
		String profile) {
}