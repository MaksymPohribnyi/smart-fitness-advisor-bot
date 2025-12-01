package com.ua.pohribnyi.fitadvisorbot.model.entity.strava;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.ua.pohribnyi.fitadvisorbot.enums.ActivityType;
import com.ua.pohribnyi.fitadvisorbot.model.entity.user.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "strava_activities")
@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class StravaActivity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Column(name = "strava_activity_id", unique = true, nullable = false)
	private Long stravaActivityId;

	@Column(name = "name", nullable = false)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(name = "activity_type", nullable = false, length = 50)
	private ActivityType activityType;

	@Column(name = "start_date_local", nullable = false)
	private LocalDateTime startDateLocal;

	@Column(name = "timezone")
	private String timezone;

	// Distance metrics
	@Column(name = "distance")
	private Double distance; // meters

	@Column(name = "moving_time")
	private Integer movingTime; // seconds

	@Column(name = "elapsed_time")
	private Integer elapsedTime; // seconds

	// Elevation metrics
	@Column(name = "total_elevation_gain")
	private Float totalElevationGain; // meters

	@Column(name = "elev_high")
	private Float elevHigh;

	@Column(name = "elev_low")
	private Float elevLow;

	// Speed metrics
	@Column(name = "average_speed")
	private Float averageSpeed; // m/s

	@Column(name = "max_speed")
	private Float maxSpeed; // m/s

	// Heart rate metrics
	@Column(name = "average_heartrate")
	private Float averageHeartrate;

	@Column(name = "max_heartrate")
	private Float maxHeartrate;

	@Column(name = "has_heartrate")
	private Boolean hasHeartrate;

	// Power metrics
	@Column(name = "average_watts")
	private Float averageWatts;

	@Column(name = "max_watts")
	private Float maxWatts;

	@Column(name = "weighted_average_watts")
	private Float weightedAverageWatts;

	@Column(name = "kilojoules")
	private Float kilojoules;

	// Cadence metrics
	@Column(name = "average_cadence")
	private Float averageCadence;

	// Energy metrics
	@Column(name = "calories")
	private Integer calories;

	// Additional info
	@Column(name = "description", columnDefinition = "TEXT")
	private String description;

	@Column(name = "device_name")
	private String deviceName;

	@Column(name = "gear_id")
	private String gearId;

	// Activity flags
	@Column(name = "commute")
	private Boolean commute;

	@Column(name = "trainer")
	private Boolean trainer;

	@Column(name = "manual")
	private Boolean manual;

	@Column(name = "private")
	private Boolean privateActivity;

	@Column(name = "flagged")
	private Boolean flagged;

	// Achievement counts
	@Column(name = "achievement_count")
	private Integer achievementCount;

	@Column(name = "kudos_count")
	private Integer kudosCount;

	@Column(name = "comment_count")
	private Integer commentCount;

	@Column(name = "athlete_count")
	private Integer athleteCount;

	@Column(name = "photo_count")
	private Integer photoCount;

	// Perceived exertion (for ML/analytics)
	@Column(name = "perceived_exertion")
	private Integer perceivedExertion; // 1-10 scale

	@Column(name = "suffer_score")
	private Integer sufferScore; // Strava's suffer score

	// Weather data (if available)
	@Column(name = "temperature")
	private Float temperature;

	// Lap and split data flags
	@Column(name = "has_laps")
	private Boolean hasLaps;

	@Column(name = "splits_metric_count")
	private Integer splitsMetricCount;

	// Sync metadata
	@Column(name = "last_synced_at")
	private LocalDateTime lastSyncedAt;

	@CreationTimestamp
	@Column(name = "created_at", updatable = false)
	private LocalDateTime createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at")
	private LocalDateTime updatedAt;

	// Computed fields for analytics
	@Column(name = "pace")
	private Float pace; // min/km

	@Column(name = "training_load")
	private Float trainingLoad; // computed metric for ML

}
