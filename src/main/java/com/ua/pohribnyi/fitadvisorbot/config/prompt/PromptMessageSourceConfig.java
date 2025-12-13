package com.ua.pohribnyi.fitadvisorbot.config.prompt;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;

@Configuration
public class PromptMessageSourceConfig {

	@Bean("promptMessageSource")
	public PromptMessageSource promptMessageSource(ResourceLoader resourceLoader) {
		PromptMessageSource messageSource = new PromptMessageSource();
		messageSource.setBasenames("classpath:prompts/prompts");
		messageSource.setDefaultEncoding("UTF-8");
		messageSource.setUseCodeAsDefaultMessage(true);
		messageSource.setFallbackToSystemLocale(false);
		messageSource.setResourceLoader(resourceLoader);
		// messageSource.setCacheSeconds(3600); doesn`t works with YML?
		return messageSource;
	}
}
