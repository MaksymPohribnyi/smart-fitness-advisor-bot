package com.ua.pohribnyi.fitadvisorbot.model.entity.strava;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.ua.pohribnyi.fitadvisorbot.model.entity.user.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "strava_athletes")
public class StravaAthlete {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne
	@JoinColumn(name = "user_id", unique = true, nullable = false)
	private User user;

	@Column(name = "strava_athlete_id", unique = true, nullable = false)
	private Long stravaAthleteId;

	@Column(name = "first_name")
	private String firstName;

	@Column(name = "last_name")
	private String lastName;

	@Column(name = "username")
	private String username;

	@Column(name = "city")
	private String city;

	@Column(name = "state")
	private String state;

	@Column(name = "country")
	private String country;

	@Column(name = "sex", length = 1)
	private String sex;

	@Column(name = "summit")
	private Boolean summit;

	@Column(name = "weight")
	private Float weight;

	@Column(name = "follower_count")
	private Integer followerCount;

	@Column(name = "friend_count")
	private Integer friendCount;

	@Column(name = "athlete_type")
	private String athleteType; // runner, cyclist, etc.

	@Column(name = "strava_created_at")
	private LocalDateTime stravaCreatedAt;

	@Column(name = "strava_updated_at")
	private LocalDateTime stravaUpdatedAt;

	// FTP (Functional Threshold Power) for cyclists
	@Column(name = "ftp")
	private Integer ftp;

	@Column(name = "measurement_preference")
	private String measurementPreference; // feet or meters

	@Column(name = "bikes_count")
	private Integer bikesCount;

	@Column(name = "shoes_count")
	private Integer shoesCount;

	@Column(name = "total_activities_count")
	private Integer totalActivitiesCount;

	@Column(name = "last_activity_date")
	private LocalDateTime lastActivityDate;

	@Column(name = "last_synced_at")
	private LocalDateTime lastSyncedAt;

	@CreationTimestamp
	@Column(name = "created_at", updatable = false)
	private LocalDateTime createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at")
	private LocalDateTime updatedAt;

	public String getFullName() {
		if (firstName == null && lastName == null) {
			return "Unknown Athlete";
		}
		StringBuilder name = new StringBuilder();
		if (firstName != null) {
			name.append(firstName);
		}
		if (lastName != null) {
			if (!name.isEmpty()) {
				name.append(" ");
			}
			name.append(lastName);
		}
		return name.toString();
	}

	public String getLocation() {
		StringBuilder location = new StringBuilder();
		if (city != null && !city.isBlank()) {
			location.append(city);
		}
		if (state != null && !state.isBlank()) {
			if (!location.isEmpty())
				location.append(", ");
			location.append(state);
		}
		if (country != null && !country.isBlank()) {
			if (!location.isEmpty())
				location.append(", ");
			location.append(country);
		}
		return location.isEmpty() ? null : location.toString();
	}

}
