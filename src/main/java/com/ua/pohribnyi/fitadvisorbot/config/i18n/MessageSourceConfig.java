package com.ua.pohribnyi.fitadvisorbot.config.i18n;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;

@Configuration
public class MessageSourceConfig {

	@Bean("yamlMessageSource")
	public MessageSource yamlMessageSource(ResourceLoader resourceLoader) {
		YamlMessageSource messageSource = new YamlMessageSource();
		messageSource.setBasenames("classpath:messages/messages", "classpath:messages/analytics");
		messageSource.setDefaultEncoding("UTF-8");
		messageSource.setUseCodeAsDefaultMessage(true);
		messageSource.setFallbackToSystemLocale(false);
		messageSource.setResourceLoader(resourceLoader);
		messageSource.setCacheSeconds(3600); 
		return messageSource;
	}

}
