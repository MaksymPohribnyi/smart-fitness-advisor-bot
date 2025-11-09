package com.ua.pohribnyi.fitadvisorbot.model.dto.strava;

public record StravaOAuthRequestDto(String code, String state, String scope) {
}
