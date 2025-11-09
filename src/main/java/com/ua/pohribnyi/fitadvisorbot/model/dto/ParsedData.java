package com.ua.pohribnyi.fitadvisorbot.model.dto;

import java.util.List;

import com.ua.pohribnyi.fitadvisorbot.model.dto.google.ActivityDto;
import com.ua.pohribnyi.fitadvisorbot.model.dto.google.DailyMetricDto;

public record ParsedData(List<DailyMetricDto> metricDtos, List<ActivityDto> activityDtos) {

}
