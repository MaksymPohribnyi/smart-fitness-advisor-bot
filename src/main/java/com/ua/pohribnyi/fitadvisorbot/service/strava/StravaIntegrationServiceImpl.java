package com.ua.pohribnyi.fitadvisorbot.service.strava;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import com.ua.pohribnyi.fitadvisorbot.config.StravaConfig;
import com.ua.pohribnyi.fitadvisorbot.model.dto.strava.StravaAuthStatusDto;
import com.ua.pohribnyi.fitadvisorbot.model.dto.strava.StravaTokenResponseDto;
import com.ua.pohribnyi.fitadvisorbot.model.entity.strava.OAuthState;
import com.ua.pohribnyi.fitadvisorbot.model.entity.strava.StravaToken;
import com.ua.pohribnyi.fitadvisorbot.model.entity.user.User;
import com.ua.pohribnyi.fitadvisorbot.repository.strava.StravaTokenRepository;
import com.ua.pohribnyi.fitadvisorbot.repository.user.UserRepository;
import com.ua.pohribnyi.fitadvisorbot.service.ouath.OAuthStateService;
import com.ua.pohribnyi.fitadvisorbot.service.token.TokenEncryptionService;
import com.ua.pohribnyi.fitadvisorbot.util.exception.StravaAuthException;
import com.ua.pohribnyi.fitadvisorbot.util.exception.StravaException;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class StravaIntegrationServiceImpl implements StravaIntegrationService {

	private final StravaConfig stravaConfig;
	private final StravaApiClient stravaApiClient;
	private final OAuthStateService OAuthStateService;
	private final TokenEncryptionService tokenEncryptionService;
	private final UserRepository userRepository;
	private final StravaTokenRepository stravaTokenRepository;
	// TODO:
	// remove sending message from this service through bot service
	//private final FitnessAdvisorBotService fitnessAdvisorBotService;
	


	public StravaIntegrationServiceImpl(StravaConfig stravaConfig, StravaApiClient stravaApiClient,
			OAuthStateService oAuthStateService,
			TokenEncryptionService tokenEncryptionService, UserRepository userRepository,
			StravaTokenRepository stravaTokenRepository) {
		this.stravaConfig = stravaConfig;
		this.stravaApiClient = stravaApiClient;
		this.OAuthStateService  = oAuthStateService;
		this.tokenEncryptionService = tokenEncryptionService;
		this.userRepository = userRepository;
		this.stravaTokenRepository = stravaTokenRepository;
		//this.fitnessAdvisorBotService = fitnessAdvisorBotService;
	}

	@Override
	public String getAuthorizationUrl(Long telegramUserId) {
		log.info("Generating Strava authorization URL for user: {}", telegramUserId);

		OAuthState state = OAuthStateService.createState(telegramUserId, "strava");

		String authUrl = UriComponentsBuilder.fromUriString(stravaConfig.getAuthorizeUrl())
				.queryParam("client_id", stravaConfig.getClientId())
				.queryParam("response_type", "code")
				.queryParam("redirect_uri", stravaConfig.getRedirectUri())
				.queryParam("approval_prompt", "force")
				.queryParam("scope", stravaConfig.getScopes())
				.queryParam("state", state.getStateToken())
				.toUriString();

		log.debug("Authorization URL generated successfully");
		return authUrl;
	}

	@Override
	@Transactional
	public User handleAuthorizationCallback(Long telegramUserId, String code, String state, String permittedScopes) {
		log.info("Processing Strava authorization callback for user: {}", telegramUserId);

		try {
			OAuthState oauthState = OAuthStateService.findAndValidateState(state, "strava");
			if (!oauthState.getTelegramUserId().equals(telegramUserId)) {
				throw new StravaAuthException("OAuth state user mismatch");
			}

			StravaTokenResponseDto tokenResponse = stravaApiClient.exchangeCodeForToken(code);

			User user = userRepository.findByTelegramUserId(telegramUserId)
					.orElseThrow(() -> new StravaAuthException("User not found"));

			saveToken(user, tokenResponse, permittedScopes);

            OAuthStateService.markAsUsed(state);

			log.info("User {} successfully connected to Strava, athlete ID: {}", telegramUserId,
					tokenResponse.athlete().id());

			return user;

		} catch (StravaException e) {
			log.error("Strava authorization failed for user {}: {}", telegramUserId, e.getMessage(), e);
			throw e;
		} catch (Exception e) {
			log.error("Unexpected error during Strava authorization for user {}: {}", telegramUserId, e.getMessage(),
					e);
			throw new StravaException("Authorization failed", e);
		}

	}

	@Override
	public StravaAuthStatusDto getAuthStatus(Long telegramUserId) {
		log.debug("Getting Strava auth status for user: {}", telegramUserId);

		User user = userRepository.findByTelegramUserId(telegramUserId)
				.orElseThrow(() -> new StravaException("User not found"));

		return stravaTokenRepository.findByUser(user)
	            .map(token -> new StravaAuthStatusDto(
	                token.getStravaAthleteId(),
	                user.getFirstName() + " " + user.getLastName(),
	                true,
	                tokenIsExpired(token),
	                token.getUpdatedAt() != null ? token.getUpdatedAt().toString() : null
	            ))
	            .orElseGet(() -> new StravaAuthStatusDto(null, null, false, false, null));
	}

	@Override
	public void ensureValidToken(User user) {
		log.debug("Ensuring valid Strava token for user: {}", user.getId());

		StravaToken token = stravaTokenRepository.findByUser(user)
				.orElseThrow(() -> new StravaAuthException("User is not connected to Strava"));

		if (tokenIsExpired(token)) {
			log.info("Token expired for user {}, refreshing...", user.getTelegramUserId());
			refreshToken(token);
		} else {
			log.debug("Token is still valid for user: {}", user.getTelegramUserId());
		}
	}

	@Override
	@Transactional
	public void disconnectStrava(Long telegramUserId) {
		log.info("Disconnecting user {} from Strava", telegramUserId);

        User user = userRepository.findByTelegramUserId(telegramUserId)
            .orElseThrow(() -> new StravaException("User not found"));

		stravaTokenRepository.findByUser(user).ifPresent(token -> {
			stravaTokenRepository.delete(token);
            log.info("Deleted Strava token for user {}", telegramUserId);
		});

        log.info("User {} disconnected from Strava", telegramUserId);
	}

	private void saveToken(User user, StravaTokenResponseDto tokenResponse, String permittedScopes) {
		stravaTokenRepository.findByUser(user).ifPresent(oldToken -> {
			log.debug("Deleting old token for user {}", user.getTelegramUserId());
			stravaTokenRepository.delete(oldToken);
		});

		StravaToken newToken = StravaToken.builder()
				.user(user)
				.stravaAthleteId(tokenResponse.athlete().id())
				.accessToken(tokenEncryptionService.encrypt(tokenResponse.accessToken()))
				.refreshToken(tokenEncryptionService.encrypt(tokenResponse.refreshToken()))
				.expiresAt(LocalDateTime.now().plusSeconds(tokenResponse.expiresIn()))
				.scope(permittedScopes)
				.build();

        stravaTokenRepository.save(newToken);
        log.info("Saved new Strava token for user {}", user.getTelegramUserId());
    }
	
	@Transactional
	private void refreshToken(StravaToken token) {
		try {
			String refreshToken = tokenEncryptionService.decrypt(token.getRefreshToken());
			StravaTokenResponseDto tokenResponse = stravaApiClient.refreshAccessToken(refreshToken);

			token.setAccessToken(tokenEncryptionService.encrypt(tokenResponse.accessToken()));
			token.setRefreshToken(tokenEncryptionService.encrypt(tokenResponse.refreshToken()));
			token.setExpiresAt(LocalDateTime.now().plusSeconds(tokenResponse.expiresIn()));

			stravaTokenRepository.save(token);

			log.info("Token refreshed successfully for user: {}", token.getUser().getTelegramUserId());

		} catch (Exception e) {
			log.error("Failed to refresh token for user {}: {}", token.getUser().getTelegramUserId(), e.getMessage(),
					e);
            stravaTokenRepository.delete(token);
			throw new StravaAuthException("Failed to refresh Strava token", e);
		}
	}

	private boolean tokenIsExpired(StravaToken token) {
		return token.getExpiresAt() == null
				|| LocalDateTime.now().isAfter(token.getExpiresAt());
	}
	
	/*
	 * private void notifyAuthFailure(Long telegramUserId, String errorMessage) {
	 * try { String message =
	 * String.format(MessageTemplates.STRAVA_CONNECT_FAILURE_MESSAGE, errorMessage);
	 * //fitnessAdvisorBotService.sendMessage(telegramUserId, message); } catch
	 * (Exception e) { log.error("Failed to send auth failure notification: {}",
	 * e.getMessage()); } }
	 */
	
}
