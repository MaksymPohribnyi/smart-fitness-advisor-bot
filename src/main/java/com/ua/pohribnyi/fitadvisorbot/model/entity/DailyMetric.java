package com.ua.pohribnyi.fitadvisorbot.model.entity;

import java.time.LocalDate;

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
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "daily_metrics")
@Data
@NoArgsConstructor
public class DailyMetric {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "daily_metrics_seq")
	@SequenceGenerator(name = "daily_metrics_seq", sequenceName = "daily_metrics_id_seq", allocationSize = 50)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Column(name = "metric_date", nullable = false)
	private LocalDate date;

	@Column(name = "sleep_hours")
	private Double sleepHours;

	@Column(name = "daily_base_steps")
	private Integer dailyBaseSteps;

	@Column(name = "stress_level")
	private Integer stressLevel; // 1-5

	@Column(name = "is_synthetic", nullable = false)
	private boolean isSynthetic = false;
}
