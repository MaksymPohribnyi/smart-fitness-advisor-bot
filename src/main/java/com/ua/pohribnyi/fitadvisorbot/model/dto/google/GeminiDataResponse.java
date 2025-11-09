package com.ua.pohribnyi.fitadvisorbot.model.dto.google;

import java.util.List;

//This is the root object of the JSON response
public record GeminiDataResponse(List<DailyMetricDto> dailyMetrics, List<ActivityDto> activities) {
}
