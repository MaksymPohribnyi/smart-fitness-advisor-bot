package com.ua.pohribnyi.fitadvisorbot.model.dto.google;

import java.time.LocalDate;

import com.ua.pohribnyi.fitadvisorbot.model.entity.DailyMetric;
import com.ua.pohribnyi.fitadvisorbot.model.entity.user.User;

public record DailyMetricDto(String date, Float sleepHours, Integer dailyBaseSteps, Integer stressLevel) {

	public static DailyMetric mapToEntity(DailyMetricDto dto, User user) {
		DailyMetric entity = new DailyMetric();
		entity.setUser(user);
		entity.setDate(LocalDate.parse(dto.date()));
		entity.setSleepHours(dto.sleepHours());
		entity.setDailyBaseSteps(dto.dailyBaseSteps());
		entity.setStressLevel(dto.stressLevel());
		entity.setSynthetic(true);
		return entity;
	}

}
