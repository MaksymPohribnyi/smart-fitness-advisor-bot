package com.ua.pohribnyi.fitadvisorbot.repository.strava;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ua.pohribnyi.fitadvisorbot.model.entity.strava.StravaToken;
import com.ua.pohribnyi.fitadvisorbot.model.entity.user.User;

public interface StravaTokenRepository extends JpaRepository<StravaToken, Long> {

	Optional<StravaToken> findByUser(User user);

	Optional<StravaToken> findByUser_TelegramUserId(Long telegramUserId);

	Optional<StravaToken> findByStravaAthleteId(Long stravaAthleteId);

	boolean existsByUser_TelegramUserId(Long telegramUserId);

	void deleteByUser(User user);

}
