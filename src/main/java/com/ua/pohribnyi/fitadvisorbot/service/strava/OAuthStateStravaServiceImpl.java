package com.ua.pohribnyi.fitadvisorbot.service.strava;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ua.pohribnyi.fitadvisorbot.model.entity.strava.OAuthState;
import com.ua.pohribnyi.fitadvisorbot.repository.oauth.OAuthStateRepository;
import com.ua.pohribnyi.fitadvisorbot.service.ouath.OAuthStateService;
import com.ua.pohribnyi.fitadvisorbot.util.exception.StravaAuthException;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class OAuthStateStravaServiceImpl implements OAuthStateService {

	private final OAuthStateRepository OAuthStateRepository;
	// OAuth state token validity: 15 minutes
    private static final int STATE_EXPIRY_MINUTES = 15;
	

	public OAuthStateStravaServiceImpl(OAuthStateRepository oAuthStateRepository) {
		this.OAuthStateRepository = oAuthStateRepository;
	}

	@Override
	@Transactional
	public OAuthState createState(Long telegramUserId, String provider) {
		log.info("Creating OAuth state for user: {}, provider: {}", telegramUserId, provider);
		// TODO:
		// if 2 state are returned than exception is invoked 
		// should to define mechanism to delete old states from table
        // Delete previous unused state for this user and provider
		/*
		 * OAuthStateRepository.findByTelegramUserIdAndProvider(telegramUserId,
		 * provider) .ifPresent(existingState -> { if (!existingState.getIsUsed()) {
		 * OAuthStateRepository.delete(existingState); } });
		 */
        String stateToken = UUID.randomUUID().toString();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(STATE_EXPIRY_MINUTES);

		OAuthState oauthState = OAuthState.builder()
				.stateToken(stateToken)
				.telegramUserId(telegramUserId)
				.provider(provider)
				.expiresAt(expiresAt)
				.build();

        OAuthState saved = OAuthStateRepository.save(oauthState);
        log.debug("OAuth state created successfully: {}", stateToken);
        return saved;	
	}

	@Override
	public OAuthState findAndValidateState(String stateToken, String provider) {
		log.debug("Validating OAuth state: {}, provider: {}", stateToken, provider);

		OAuthState state = OAuthStateRepository.findByStateToken(stateToken)
				.orElseThrow(() -> new StravaAuthException("Invalid OAuth state token"));

		if (!state.getProvider().equals(provider)) {
			log.warn("Provider mismatch for state token: {}", stateToken);
			throw new StravaAuthException("Provider mismatch in OAuth state");
		}

		if (OAuthStateInvalid(state)) {
			log.warn("OAuth state is invalid (expired or already used): {}", stateToken);
			throw new StravaAuthException("OAuth state token has expired or been used");
		}

		return state;
	}

	@Override
	@Transactional
	public void markAsUsed(String stateToken) {
		log.debug("Marking OAuth state as used: {}", stateToken);

		OAuthState state = OAuthStateRepository.findByStateToken(stateToken)
				.orElseThrow(() -> new StravaAuthException("OAuth state not found"));

		//state.setIsUsed(true);
		OAuthStateRepository.save(state);
	}

	@Override
	@Scheduled(fixedDelay = 3600000) // Run every hour
	@Transactional
	public void cleanupExpiredStates() {
		log.info("Cleaning up expired OAuth states");

		LocalDateTime expiryTime = LocalDateTime.now();
		OAuthStateRepository.deleteByExpiresAtBefore(expiryTime);

		log.debug("OAuth state cleanup completed");
	}

	private boolean OAuthStateInvalid(OAuthState state) {
		return LocalDateTime.now().isAfter(state.getExpiresAt());
	}

}
