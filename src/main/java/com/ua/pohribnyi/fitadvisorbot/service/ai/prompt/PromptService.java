package com.ua.pohribnyi.fitadvisorbot.service.ai.prompt;

import java.util.Locale;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
public class PromptService {

	public PromptService(@Qualifier("promptMessageSource") MessageSource promptMessageSource) {
		this.promptMessageSource = promptMessageSource;
	}

	private final MessageSource promptMessageSource;
	
	/**
	 * Форматує промпт з параметрами
	 * 
	 * @param key  ключ промпту (ai.daily-advice, ai.history-generation)
	 * @param args параметри для String.format()
	 */
	public String format(String key, Object... args) {
		String template = promptMessageSource.getMessage(key, null, Locale.ENGLISH);
		return String.format(Locale.US, template, args);
	}

	public String getRaw(String key) {
		return promptMessageSource.getMessage(key, null, Locale.ENGLISH);
	}

	public boolean exists(String key) {
		try {
			promptMessageSource.getMessage(key, null, Locale.ENGLISH);
			return true;
		} catch (Exception e) {
			return false;
		}
	}
}