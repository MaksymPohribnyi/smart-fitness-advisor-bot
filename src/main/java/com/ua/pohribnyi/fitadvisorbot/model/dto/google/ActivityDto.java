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
		entity.setDurationSeconds(dto.durationSeconds());
		entity.setDistanceMeters(dto.distanceMeters());
		entity.setAvgPulse(dto.avgPulse());
		entity.setMaxPulse(dto.maxPulse());
		entity.setMinPulse(dto.minPulse());
		entity.setCaloriesBurned(dto.caloriesBurned());
		entity.setActivitySteps(dto.activitySteps());
		entity.setSynthetic(true);
		return entity;
	}
	
}