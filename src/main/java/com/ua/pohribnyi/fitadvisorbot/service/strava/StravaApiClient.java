package com.ua.pohribnyi.fitadvisorbot.service.strava;

import java.util.List;

import com.ua.pohribnyi.fitadvisorbot.model.dto.strava.StravaActivityDto;
import com.ua.pohribnyi.fitadvisorbot.model.dto.strava.StravaAthleteDto;
import com.ua.pohribnyi.fitadvisorbot.model.dto.strava.StravaTokenResponseDto;

public interface StravaApiClient {

	StravaTokenResponseDto exchangeCodeForToken(String code);

	StravaTokenResponseDto refreshAccessToken(String refreshToken);

	StravaAthleteDto getAthlete(String accessToken);

	StravaActivityDto getActivity(String accessToken, Long activityId);
	
	List<StravaActivityDto> getAthleteActivities(String accessToken, Integer limit, Integer page);

	boolean validateToken(String accessToken);
}
