package com.ua.pohribnyi.fitadvisorbot.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import lombok.Getter;

@Configuration
@Getter
public class StravaConfig {

	@Value("${strava.client-id}")
	private String clientId;

	@Value("${strava.client-secret}")
	private String clientSecret;

	@Value("${strava.redirect-uri}")
	private String redirectUri;

	@Value("${strava.api.base-url}")
	private String apiBaseUrl;

	@Value("${strava.auth.token-url}")
	private String tokenUrl;

	@Value("${strava.auth.authorize-url}")
	private String authorizeUrl;

	@Value("${strava.api.activities-url}")
	private String activitiesUrl;

	@Value("${strava.scopes}")
	private String scopes;

	public String getOAuthState(String userId) {
		return "strava_oauth_" + userId + "_" + System.currentTimeMillis();
	}
}
