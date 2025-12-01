package com.ua.pohribnyi.fitadvisorbot.model.dto.google;

import java.time.LocalDateTime;

import com.ua.pohribnyi.fitadvisorbot.model.entity.Activity;
import com.ua.pohribnyi.fitadvisorbot.model.entity.user.User;

public record ActivityDto(
		LocalDateTime dateTime, 
		String type, 
		Integer durationSeconds, 
		Integer distanceMeters,
		Integer avgPulse, 
		Integer maxPulse, 
		Integer minPulse, 
		Integer caloriesBurned, 
		Integer activitySteps) {
	
	public static Activity mapToEntity(ActivityDto dto, User user) {
		Activity entity = new Activity();
		entity.setUser(user);
		entity.setDateTime(dto.dateTime());
		entity.setType(dto.type());
		entity.setDurationSeconds(dto.durationSeconds() != null ? dto.durationSeconds() : 0);
		entity.setDistanceMeters(dto.distanceMeters() != null ? dto.distanceMeters() : 0);
		entity.setAvgPulse(dto.avgPulse() != null ? dto.avgPulse() : 0);
		entity.setMaxPulse(dto.maxPulse() != null ? dto.maxPulse() : 0);
		entity.setMinPulse(dto.minPulse() != null ? dto.minPulse() : 0);
		entity.setCaloriesBurned(dto.caloriesBurned());
		entity.setActivitySteps(dto.activitySteps() != null ? dto.activitySteps() : 0);
		entity.setSynthetic(true);
		return entity;
	}
	
}