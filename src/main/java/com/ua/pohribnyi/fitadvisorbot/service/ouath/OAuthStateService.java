package com.ua.pohribnyi.fitadvisorbot.service.ouath;

import com.ua.pohribnyi.fitadvisorbot.model.entity.strava.OAuthState;

public interface OAuthStateService {

	OAuthState createState(Long telegramUserId, String provider);

	OAuthState findAndValidateState(String stateToken, String provider);

	void markAsUsed(String stateToken);

	void cleanupExpiredStates();

}
