package com.ua.pohribnyi.fitadvisorbot.repository.strava;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.ua.pohribnyi.fitadvisorbot.enums.ActivityType;
import com.ua.pohribnyi.fitadvisorbot.model.entity.strava.StravaActivity;
import com.ua.pohribnyi.fitadvisorbot.model.entity.user.User;

@Repository
public interface StravaActivityRepository extends JpaRepository<StravaActivity, Long> {

	Optional<StravaActivity> findByStravaActivityId(Long stravaActivityId);

	/**
	 * Find all activities for user ordered by date descending
	 */
	List<StravaActivity> findByUserOrderByStartDateLocalDesc(User user);

	/**
	 * Find activities for user within date range
	 */
	@Query("SELECT a FROM StravaActivity a WHERE a.user = :user AND a.startDateLocal BETWEEN :startDate AND :endDate ORDER BY a.startDateLocal DESC")
	List<StravaActivity> findByUserAndDateRange(@Param("user") User user, @Param("startDate") LocalDateTime startDate,
			@Param("endDate") LocalDateTime endDate);

	/**
	 * Find activities by type for user
	 */
	List<StravaActivity> findByUserAndActivityTypeOrderByStartDateLocalDesc(User user, ActivityType activityType);

	/**
	 * Find recent activities with pagination
	 */
	List<StravaActivity> findByUserOrderByStartDateLocalDesc(User user, Pageable pageable);

	/**
	 * Count activities for user
	 */
	long countByUser(User user);

	/**
	 * Count activities by type for user
	 */
	long countByUserAndActivityType(User user, ActivityType activityType);

	/**
	 * Delete all activities for user
	 */
	@Modifying
	void deleteByUser(User user);

	/**
	 * Get total distance for user
	 */
	@Query("SELECT COALESCE(SUM(a.distance), 0) FROM StravaActivity a WHERE a.user = :user")
	Double getTotalDistance(@Param("user") User user);

	/**
	 * Get total moving time for user
	 */
	@Query("SELECT COALESCE(SUM(a.movingTime), 0) FROM StravaActivity a WHERE a.user = :user")
	Long getTotalMovingTime(@Param("user") User user);

	/**
	 * Get total calories for user
	 */
	@Query("SELECT COALESCE(SUM(a.calories), 0) FROM StravaActivity a WHERE a.user = :user")
	Integer getTotalCalories(@Param("user") User user);

	/**
	 * Get total elevation gain for user
	 */
	@Query("SELECT COALESCE(SUM(a.totalElevationGain), 0) FROM StravaActivity a WHERE a.user = :user")
	Double getTotalElevationGain(@Param("user") User user);

	/**
	 * Get latest activity date for user
	 */
	@Query("SELECT MAX(a.startDateLocal) FROM StravaActivity a WHERE a.user = :user")
	Optional<LocalDateTime> getLatestActivityDate(@Param("user") User user);

	/**
	 * Get activities with heart rate data
	 */
	@Query("SELECT a FROM StravaActivity a WHERE a.user = :user AND a.hasHeartrate = true ORDER BY a.startDateLocal DESC")
	List<StravaActivity> findActivitiesWithHeartRate(@Param("user") User user);

}
