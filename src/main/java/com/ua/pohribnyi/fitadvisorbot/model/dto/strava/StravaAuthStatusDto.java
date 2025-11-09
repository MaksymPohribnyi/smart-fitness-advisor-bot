package com.ua.pohribnyi.fitadvisorbot.model.dto.strava;

public record StravaAuthStatusDto(Long stravaAthleteId, String athleteName, boolean isConnected, boolean isTokenExpired,
		String lastSyncTime) {
}
