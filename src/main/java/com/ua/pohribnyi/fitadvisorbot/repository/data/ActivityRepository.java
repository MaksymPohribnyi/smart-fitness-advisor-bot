package com.ua.pohribnyi.fitadvisorbot.repository.data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ua.pohribnyi.fitadvisorbot.model.entity.Activity;
import com.ua.pohribnyi.fitadvisorbot.model.entity.user.User;

public interface ActivityRepository extends JpaRepository<Activity, Long> {

	/**
	 * Counts activities for the user within the specified time range.
	 */
	@Query("SELECT COUNT(a) FROM Activity a WHERE a.user = :user AND a.dateTime >= :since")
	Integer countActivitiesByUserAndDateAfter(@Param("user") User user, @Param("since") LocalDateTime since);

	/**
	 * Calculates total distance in meters.
	 */
	@Query("SELECT SUM(a.distanceMeters) FROM Activity a WHERE a.user = :user AND a.dateTime >= :since")
	Optional<Long> getTotalDistanceByUserAndDateAfter(@Param("user") User user, @Param("since") LocalDateTime since);

	/**
	 * Calculates total calories burned.
	 */
	@Query("SELECT SUM(a.caloriesBurned) FROM Activity a WHERE a.user = :user AND a.dateTime >= :since")
	Optional<Integer> getTotalCaloriesByUserAndDateAfter(@Param("user") User user, @Param("since") LocalDateTime since);

	/**
	 * Calculates total duration in seconds.
	 */
	@Query("SELECT SUM(a.durationSeconds) FROM Activity a WHERE a.user = :user AND a.dateTime >= :since")
	Optional<Long> getTotalDurationByUserAndDateAfter(@Param("user") User user, @Param("since") LocalDateTime since);

	/**
	 * Fetches raw activity list for complex analysis (trends, consistency). Ordered
	 * by date to simplify calculations.
	 */
	@Query("SELECT a FROM Activity a WHERE a.user = :user AND a.dateTime >= :since ORDER BY a.dateTime ASC")
	List<Activity> findActivitiesByUserAndDateAfter(@Param("user") User user, @Param("since") LocalDateTime since);

}
