package com.ua.pohribnyi.fitadvisorbot.model.dto.analytics;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class DiaryDraft {
	private Double sleepHours;
	private Integer stressLevel;
	private boolean hadActivity;

	// Activity details
	private String activityType;
	private Integer durationMinutes;
	private Integer rpe; // 1-10
	
	// Technical field for cleanup (TTL)
	private LocalDateTime lastUpdated = LocalDateTime.now();
	
	public void touch() {
		lastUpdated = LocalDateTime.now();
	}
}