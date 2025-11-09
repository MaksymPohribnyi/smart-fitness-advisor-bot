package com.ua.pohribnyi.fitadvisorbot.model.dto;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ua.pohribnyi.fitadvisorbot.model.entity.user.User;

public record UserDto(
		Long id,
        @JsonProperty("telegram_user_id")
        Long telegramUserId,
        @JsonProperty("telegram_username") 
        String telegramUsername,
        @JsonProperty("first_name")
        String firstName,
        @JsonProperty("last_name")
        String lastName,
       @JsonProperty("language_code")
       String languageCode,
//        String timezone,
        @JsonProperty("created_at")
        LocalDateTime createdAt,
        @JsonProperty("updated_at")
        LocalDateTime updatedAt) {
	
	/**
	 * @param user
	 * @return
	 */
	public static UserDto fromEntity(User user) {
		return new UserDto(
				user.getId(), 
				user.getTelegramUserId(), 
				user.getTelegramUsername(), 
				user.getFirstName(),
                user.getLastName(),
                user.getLanguageCode(),
				user.getCreatedAt(), 
				user.getUpdatedAt());
	}
}
