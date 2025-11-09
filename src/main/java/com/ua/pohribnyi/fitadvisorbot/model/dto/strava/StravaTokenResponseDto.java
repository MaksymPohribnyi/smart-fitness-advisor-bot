package com.ua.pohribnyi.fitadvisorbot.model.dto.strava;

import com.fasterxml.jackson.annotation.JsonProperty;

public record StravaTokenResponseDto(
		@JsonProperty("token_type") String tokenType,
		@JsonProperty("expires_at") Long expiresAt, 
		@JsonProperty("expires_in") Long expiresIn,
		@JsonProperty("refresh_token") String refreshToken, 
		@JsonProperty("access_token") String accessToken,
		StravaAthleteDto athlete) {
}
