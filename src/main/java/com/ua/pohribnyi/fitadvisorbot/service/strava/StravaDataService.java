package com.ua.pohribnyi.fitadvisorbot.service.strava;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ua.pohribnyi.fitadvisorbot.model.dto.strava.StravaActivityDto;
import com.ua.pohribnyi.fitadvisorbot.model.dto.strava.StravaAthleteDto;
import com.ua.pohribnyi.fitadvisorbot.model.entity.strava.StravaToken;
import com.ua.pohribnyi.fitadvisorbot.model.entity.user.User;
import com.ua.pohribnyi.fitadvisorbot.repository.strava.StravaTokenRepository;
import com.ua.pohribnyi.fitadvisorbot.repository.user.UserRepository;
import com.ua.pohribnyi.fitadvisorbot.service.token.TokenEncryptionService;
import com.ua.pohribnyi.fitadvisorbot.util.exception.StravaAuthException;
import com.ua.pohribnyi.fitadvisorbot.util.exception.StravaException;
import com.ua.pohribnyi.fitadvisorbot.util.exception.UserNotFoundException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class StravaDataService {

	private final StravaApiClient stravaApiClient;
	private final StravaIntegrationService stravaIntegrationService;
	private final UserRepository userRepository;
	private final StravaTokenRepository stravaTokenRepository;
	private final TokenEncryptionService tokenEncryptionService;

	@Transactional(readOnly = true)
	public StravaAthleteDto getAthlete(Long telegramUserId) {
		log.info("Fetching athlete data for user: {}", telegramUserId);

		User user = getUser(telegramUserId);
		StravaToken token = getActiveToken(user);
		stravaIntegrationService.ensureValidToken(user);

		String accessToken = tokenEncryptionService.decrypt(token.getAccessToken());

		try {
			return stravaApiClient.getAthlete(accessToken);
		} catch (Exception e) {
			log.error("Failed to fetch athlete data: {}", e.getMessage(), e);
			throw new StravaException("Failed to fetch athlete data", e);
		}
	}

	@Transactional(readOnly = true)
	public List<StravaActivityDto> getRecentActivities(Long telegramUserId, int limit) {
		log.info("Fetching {} recent activities for user: {}", limit, telegramUserId);

		User user = getUser(telegramUserId);
		StravaToken token = getActiveToken(user);
		stravaIntegrationService.ensureValidToken(user);

		String accessToken = tokenEncryptionService.decrypt(token.getAccessToken());

		try {
			return stravaApiClient.getAthleteActivities(accessToken, 1, limit);
		} catch (Exception e) {
			log.error("Failed to fetch activities: {}", e.getMessage(), e);
			throw new StravaException("Failed to fetch activities", e);
		}
	}

	@Transactional(readOnly = true)
	public StravaActivityDto getActivity(Long telegramUserId, Long activityId) {
		log.info("Fetching activity {} for user: {}", activityId, telegramUserId);

		User user = getUser(telegramUserId);
		StravaToken token = getActiveToken(user);
		stravaIntegrationService.ensureValidToken(user);

		String accessToken = tokenEncryptionService.decrypt(token.getAccessToken());

		try {
			return stravaApiClient.getActivity(accessToken, activityId);
		} catch (Exception e) {
			log.error("Failed to fetch activity: {}", e.getMessage(), e);
			throw new StravaException("Failed to fetch activity", e);
		}
	}

	private User getUser(Long telegramUserId) {
		return userRepository.findByTelegramUserId(telegramUserId)
				.orElseThrow(() -> new UserNotFoundException("User not found by telegram user id: " + telegramUserId));
	}

	private StravaToken getActiveToken(User user) {
		return stravaTokenRepository.findByUser(user)
				.orElseThrow(() -> new StravaAuthException("No active Strava token found"));
	}
}