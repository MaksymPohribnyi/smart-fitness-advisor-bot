package com.ua.pohribnyi.fitadvisorbot.repository.oauth;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.ua.pohribnyi.fitadvisorbot.model.entity.strava.OAuthState;

@Repository
public interface OAuthStateRepository extends JpaRepository<OAuthState, Long> {

	Optional<OAuthState> findByStateToken(String stateToken);

	Optional<OAuthState> findByTelegramUserIdAndProvider(Long telegramUserId, String provider);

	void deleteByExpiresAtBefore(LocalDateTime expiresAt);

}
