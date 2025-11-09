package com.ua.pohribnyi.fitadvisorbot.service.strava;

import com.ua.pohribnyi.fitadvisorbot.model.dto.strava.StravaAuthStatusDto;
import com.ua.pohribnyi.fitadvisorbot.model.entity.user.User;

public interface StravaIntegrationService {

	String getAuthorizationUrl(Long telegramUserId);

	User handleAuthorizationCallback(Long telegramUserId, String code, String state, String permittedScopes);

	StravaAuthStatusDto getAuthStatus(Long telegramUserId);

	void ensureValidToken(User user);

	void disconnectStrava(Long telegramUserId);

}
