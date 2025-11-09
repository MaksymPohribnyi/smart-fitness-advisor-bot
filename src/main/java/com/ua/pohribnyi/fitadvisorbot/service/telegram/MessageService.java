package com.ua.pohribnyi.fitadvisorbot.service.telegram;

import java.util.Locale;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.stereotype.Service;

import com.ua.pohribnyi.fitadvisorbot.service.user.UserService;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class MessageService {

	private final MessageSource messageSource;
	private final UserService userService;

	public MessageService(@Qualifier("yamlMessageSource") MessageSource messageSource, UserService userService) {
		this.messageSource = messageSource;
		this.userService = userService;
	}

	public String getMessage(String key, String languageCode, Object... args) {
		try {
			Locale locale = getLocale(languageCode);
			return messageSource.getMessage(key, args, locale);
		} catch (NoSuchMessageException e) {
			log.warn("No message found for key: {} and locale: {}", key, languageCode);
			return messageSource.getMessage(key, args, Locale.of("uk", "UA"));
		}
	}

	public String getMessageForChat(String key, Long chatId, Object... args) {
		String langCode = userService.getUserLanguageCode(chatId);
		return getMessage(key, langCode, args);
	}

	public String getLangCode(Long chatId) {
		return userService.getUserLanguageCode(chatId);
	}

	private Locale getLocale(String languageCode) {
		return switch (languageCode) {
		case "uk", "ua" -> Locale.of("uk");
		case "en" -> Locale.ENGLISH;
		default -> Locale.of("uk");
		};
	}
}