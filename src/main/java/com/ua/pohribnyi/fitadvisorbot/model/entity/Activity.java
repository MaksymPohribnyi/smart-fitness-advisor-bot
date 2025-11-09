package com.ua.pohribnyi.fitadvisorbot.model.entity;

import java.time.LocalDateTime;

import com.ua.pohribnyi.fitadvisorbot.model.entity.user.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "activities")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Activity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "activities_seq")
	@SequenceGenerator(name = "activities_seq", sequenceName = "activities_id_seq",  allocationSize = 50)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @Column(name = "activity_datetime", nullable = false)
    private LocalDateTime dateTime;

    @Column(name = "type") // "Run", "Ride"
    private String type;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "distance_meters")
    private Integer distanceMeters;

    @Column(name = "avg_pulse")
    private Integer avgPulse;

    @Column(name = "max_pulse")
    private Integer maxPulse;

    @Column(name = "min_pulse")
    private Integer minPulse;

    @Column(name = "calories")
    private Integer caloriesBurned;

    @Column(name = "activity_steps")
    private Integer activitySteps;

    @Column(name = "is_synthetic", nullable = false)
    private boolean isSynthetic = false;
}