package com.ua.pohribnyi.fitadvisorbot.service.user;

import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ua.pohribnyi.fitadvisorbot.model.entity.user.User;
import com.ua.pohribnyi.fitadvisorbot.model.entity.user.UserSession;
import com.ua.pohribnyi.fitadvisorbot.model.enums.UserState;
import com.ua.pohribnyi.fitadvisorbot.repository.user.UserSessionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserSessionService {

	// State times out after 1 hour of inactivity
	private static final Duration STATE_TIMEOUT = Duration.ofHours(1);
	private final UserSessionRepository userSessionRepository;

	@Transactional
	public UserSession findOrCreateSession(User user) {
		return userSessionRepository.findByUser(user).orElseGet(() -> createSessionForUser(user));
	}

	@Transactional
	public UserSession createSessionForUser(User user) {
		log.info("Creating new session for user_id: {}", user.getId());
		UserSession newSession = UserSession.builder()
				.user(user)
				.state(UserState.ONBOARDING_START) // Initial state
				.stateUpdatedAt(Instant.now())
				.build();
		return userSessionRepository.save(newSession);
	}

	/**
	 * Gets the user's current *active* state. If the state has expired, it resets
	 * it to DEFAULT.
	 */
	@Transactional
	public UserState getActiveState(User user) {
		UserSession session = findOrCreateSession(user);
		UserState currentState = session.getState();

		if (currentState == UserState.DEFAULT) {
			return UserState.DEFAULT;
		}

		Instant stateUpdatedAt = session.getStateUpdatedAt();
		if (stateUpdatedAt != null && Instant.now().isAfter(stateUpdatedAt.plus(STATE_TIMEOUT))) {
			log.warn("User {} state {} timed out. Resetting to DEFAULT.", user.getId(), currentState);
			setState(session, UserState.DEFAULT);
			return UserState.DEFAULT;
		}

		return currentState;
	}

	@Transactional
	public void setState(User user, UserState newState) {
		UserSession session = findOrCreateSession(user);
		setState(session, newState);
	}

	private void setState(UserSession session, UserState newState) {
		session.setState(newState);
		session.setStateUpdatedAt(Instant.now());
		userSessionRepository.save(session);
	}
}
