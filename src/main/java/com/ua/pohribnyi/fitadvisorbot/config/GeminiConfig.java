package com.ua.pohribnyi.fitadvisorbot.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentConfig;

@Configuration
public class GeminiConfig {

	/**
	 * Creates the official Google GenAI Client as a Spring Bean. The client is
	 * configured with the API key from application properties.
	 *
	 * @param apiKey Injected from application.yml (google.gemini.ai.api-key)
	 * @return A thread-safe Client instance.
	 */
	@Bean
	public Client geminiClient(@Value("${google.gemini.api.key}") String apiKey) {
		return Client.builder()
				.apiKey(apiKey)
				.build();
	}
	
	@Bean
	public GenerateContentConfig geminiGenerationConfig() {
		return GenerateContentConfig.builder()
				.temperature(0.5f)
				.topP(0.95f)
				.topK(40f)
				.maxOutputTokens(16384)    
				.build();
	}
}
