package com.ua.pohribnyi.fitadvisorbot.repository.strava;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ua.pohribnyi.fitadvisorbot.model.entity.strava.StravaAthlete;
import com.ua.pohribnyi.fitadvisorbot.model.entity.user.User;

public interface StravaAthleteRepository extends JpaRepository<StravaAthlete, Long>{

	/**
	 * Find athlete by user
	 */
	Optional<StravaAthlete> findByUser(User user);

	/**
	 * Find athlete by Strava athlete ID
	 */
	Optional<StravaAthlete> findByStravaAthleteId(Long stravaAthleteId);

	/**
	 * Check if athlete exists for user
	 */
	boolean existsByUser(User user);

}
