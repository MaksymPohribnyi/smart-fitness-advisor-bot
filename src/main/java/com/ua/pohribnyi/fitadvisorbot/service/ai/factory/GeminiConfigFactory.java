package com.ua.pohribnyi.fitadvisorbot.service.ai.factory;

import org.springframework.stereotype.Component;

import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.Schema;

@Component
public class GeminiConfigFactory {

    private static final float ANALYTICAL_TEMPERATURE = 0.5f;
    private static final float CREATIVE_TEMPERATURE = 0.9f;

    public GenerateContentConfig createStructuredConfig(Schema schema) {
        return GenerateContentConfig.builder()
                .temperature(ANALYTICAL_TEMPERATURE)
                .topP(0.95f)
                .topK(35f) 
                .maxOutputTokens(16384)
                .responseMimeType("application/json")
                .responseSchema(schema) 
                .build();
    }

    public GenerateContentConfig createCreativeConfig(Schema schema) {
        return GenerateContentConfig.builder()
                .temperature(CREATIVE_TEMPERATURE) 
                .topP(0.95f)
                .topK(40f)
                .maxOutputTokens(2048) 
                .responseMimeType("application/json")
                .responseSchema(schema)
                .build(); 
    }
}