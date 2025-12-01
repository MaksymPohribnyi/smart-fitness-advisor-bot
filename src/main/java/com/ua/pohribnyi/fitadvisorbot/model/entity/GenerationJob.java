package com.ua.pohribnyi.fitadvisorbot.model.entity;

import java.time.Instant;

import com.ua.pohribnyi.fitadvisorbot.enums.JobStatus;
import com.ua.pohribnyi.fitadvisorbot.model.entity.user.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "generation_jobs")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GenerationJob {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private JobStatus status;

	/**
	 * Raw JSON response from Gemini API. Stored temporarily until processing
	 * completes. Set to NULL after successful processing to save disk space.
	 */
	@Column(name = "raw_response", columnDefinition = "TEXT")
	private String rawResponse;

	/**
	 * Short error summary for UI display (e.g., "Rate limit exceeded"). Max 250
	 * characters to ensure it fits in DB and can be displayed.
	 */
	@Column(name = "error_message", length = 250)
	private String errorMessage;

	/**
	 * Full error details including stack trace for debugging. Stored as TEXT, can
	 * be large.
	 */
	@Column(name = "error_details", columnDefinition = "TEXT")
	private String errorDetails;

	/**
	 * Error category for monitoring and alerting. Examples: API_RATE_LIMIT,
	 * API_TIMEOUT, INVALID_JSON, DB_ERROR
	 */
	@Column(name = "error_code", length = 50)
	private String errorCode;

	@Column(name = "user_chat_id")
	private Long userChatId;

	@Column(name = "notification_message_id")
	private Integer notificationMessageId;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt = Instant.now();

	@Column(name = "completed_at")
	private Instant completedAt;

	/**
	 * Factory method to create a new, valid job. This ensures all NOT-NULL
	 * constraints are met.
	 */
	public static GenerationJob createPendingJob(User user, Long userChatId, Integer notificationId) {
		GenerationJob job = new GenerationJob();
		job.setUser(user);
		job.setStatus(JobStatus.PENDING);
		job.setCreatedAt(Instant.now());
		job.setUserChatId(userChatId);
		job.setNotificationMessageId(notificationId);
		return job;
	}

	/**
	 * Marks job as successfully completed and clears raw response.
	 */
	public void markAsProcessed() {
		this.status = JobStatus.PROCESSED;
		this.completedAt = Instant.now();
		this.rawResponse = null; // Free up disk space
	}

	/**
	 * Marks job as failed with structured error information.
	 * 
	 * @param errorCode    Category code (e.g., "API_RATE_LIMIT")
	 * @param errorMessage Short description for UI
	 * @param errorDetails Full stacktrace/details
	 */
	public void markAsFailed(String errorCode, String errorMessage, String errorDetails) {
		this.status = JobStatus.FAILED;
		this.completedAt = Instant.now();
		this.errorCode = errorCode;
		this.errorMessage = truncate(errorMessage, 250);
		this.errorDetails = truncate(errorDetails, 10000); // Limit to 10KB
		this.rawResponse = null;
	}

	/**
	 * Convenience method for simple error messages.
	 */
	public void markAsFailed(String errorCode, String errorMessage) {
		markAsFailed(errorCode, errorMessage, null);
	}

	public void stageResponse(String rawResponse) {
		this.status = JobStatus.DOWNLOADED;
		this.rawResponse = rawResponse;
	}

	public boolean isProcessable() {
		return status == JobStatus.DOWNLOADED && rawResponse != null;
	}

	private static String truncate(String text, int maxLength) {
		if (text == null)
			return null;
		if (text.length() <= maxLength)
			return text;
		return text.substring(0, maxLength - 3) + "...";
	}

}