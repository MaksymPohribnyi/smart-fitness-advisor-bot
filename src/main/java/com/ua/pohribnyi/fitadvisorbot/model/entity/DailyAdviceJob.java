package com.ua.pohribnyi.fitadvisorbot.model.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

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
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "daily_advice_jobs", uniqueConstraints = { @UniqueConstraint(columnNames = { "user_id", "job_date" }) })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyAdviceJob {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Column(name = "job_date", nullable = false)
	private LocalDate date;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false)
	private Status status;

	@Column(name = "sleep_hours")
	private Double sleepHours;

	@Column(name = "stress_level")
	private Integer stressLevel;

	@Column(name = "had_activity")
	private Boolean hadActivity;

	@Column(name = "activity_type")
	private String activityType;

	@Column(name = "duration_minutes")
	private Integer durationMinutes;

	@Column(name = "rpe")
	private Integer rpe;

	@Column(name = "advice_text", columnDefinition = "TEXT")
	private String adviceText;

	@Column(name = "error_message")
	private String errorMessage;

	@Column(name = "user_chat_id")
	private Long userChatId;

	@Column(name = "notification_message_id")
	private Integer notificationMessageId; 

	@Column(name = "created_at")
	private LocalDateTime createdAt;

	@Column(name = "updated_at")
	private LocalDateTime updatedAt;

	public enum Status {
		FILLING, // User is answering questions
		PENDING_PROCESSING, // User finished, waiting for worker
		PROCESSING, // Worker is saving data and generating AI
		COMPLETED, // Done, advice sent
		FAILED // Error happened
	}
}
