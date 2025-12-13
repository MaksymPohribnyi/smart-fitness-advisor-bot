package com.ua.pohribnyi.fitadvisorbot.model.dto.google;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DailyAdviceResponse {

	@JsonProperty("analysis")
	private String analysis;

	@JsonProperty("status")
	private String status;

	@JsonProperty("advice")
	private String advice;

	public String getFullMessage() {
		return String.join("\n\n", analysis, status, advice).trim();
	}
}