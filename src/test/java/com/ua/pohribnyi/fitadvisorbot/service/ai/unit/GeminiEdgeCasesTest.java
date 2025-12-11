package com.ua.pohribnyi.fitadvisorbot.service.ai.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.google.genai.Client;
import com.google.genai.Models;
import com.google.genai.types.Candidate;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import com.ua.pohribnyi.fitadvisorbot.repository.ai.GenerationJobRepository;
import com.ua.pohribnyi.fitadvisorbot.service.ai.GeminiApiClient;
import com.ua.pohribnyi.fitadvisorbot.service.ai.GenerationJobUpdaterService;
import com.ua.pohribnyi.fitadvisorbot.service.ai.factory.GeminiConfigFactory;
import com.ua.pohribnyi.fitadvisorbot.service.ai.schema.GeminiSchemaDefiner;

import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration;
import io.github.resilience4j.springboot3.ratelimiter.autoconfigure.RateLimiterAutoConfiguration;
import io.github.resilience4j.springboot3.retry.autoconfigure.RetryAutoConfiguration;

/**
 * Unit tests for edge cases and error handling in GeminiApiClient.
 * 
 * Testing:
 * - Empty API responses
 * - Malformed JSON responses
 * - Null/invalid response structures
 * - Response extraction fallback logic
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {
    GeminiApiClient.class,
    GeminiConfigFactory.class,
    GeminiSchemaDefiner.class
})
@EnableConfigurationProperties
@ImportAutoConfiguration(classes = {
    AopAutoConfiguration.class,
    RateLimiterAutoConfiguration.class,
    CircuitBreakerAutoConfiguration.class,
    RetryAutoConfiguration.class
})
@TestPropertySource(properties = {
    // Disable all Resilience4j patterns for pure unit testing
    "resilience4j.ratelimiter.instances.geminiApi.limit-for-period=1000",
    "resilience4j.ratelimiter.instances.geminiApi.limit-refresh-period=1s",
    "resilience4j.circuitbreaker.instances.geminiApi.minimum-number-of-calls=1000",
    "resilience4j.circuitbreaker.instances.geminiApi.failure-rate-threshold=99",
    "resilience4j.retry.instances.geminiApi.max-attempts=1",
    "google.gemini.api.key=fake-key"
})
class GeminiEdgeCasesTest {

    @Autowired
    private GeminiApiClient geminiApiClient;

    @MockitoBean
    private ApplicationEventPublisher eventPublisher;

    @MockitoBean
    private GenerationJobRepository jobRepository;

    @MockitoBean
    private GenerationJobUpdaterService jobUpdaterService;

    @MockitoBean
    private Client mockGeminiClient;

    private Models mockModels;

    @MockitoBean
    private GenerateContentConfig mockConfig;

    @BeforeEach
    void setUp() {
        Mockito.reset(mockGeminiClient);
        mockModels = mock(Models.class);
        ReflectionTestUtils.setField(mockGeminiClient, "models", mockModels);
    }

    @Test
    @DisplayName("Empty response text triggers exception")
    void emptyResponseText_triggersException() {
        // Arrange: Response with empty text
        GenerateContentResponse emptyResponse = mock(GenerateContentResponse.class);
        when(emptyResponse.text()).thenReturn("");
        when(emptyResponse.candidates()).thenReturn(Optional.empty());
        
        when(mockModels.generateContent(anyString(), anyList(), any()))
            .thenReturn(emptyResponse);

        // Act & Assert
        assertThatThrownBy(() -> geminiApiClient.callGeminiApi("test"))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("unavailable");
        
        verify(mockModels, times(1)).generateContent(anyString(), anyList(), any());
    }

    @Test
    @DisplayName("Null response text triggers exception")
    void nullResponseText_triggersException() {
        // Arrange: Response with null text
        GenerateContentResponse nullResponse = mock(GenerateContentResponse.class);
        when(nullResponse.text()).thenReturn(null);
        when(nullResponse.candidates()).thenReturn(Optional.empty());
        
        when(mockModels.generateContent(anyString(), anyList(), any()))
            .thenReturn(nullResponse);

        // Act & Assert
        assertThatThrownBy(() -> geminiApiClient.callGeminiApi("test"))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("unavailable");
    }

    @Test
    @DisplayName("Blank response text triggers exception")
    void blankResponseText_triggersException() {
        // Arrange: Response with whitespace only
        GenerateContentResponse blankResponse = mock(GenerateContentResponse.class);
        when(blankResponse.text()).thenReturn("   \n\t  ");
        when(blankResponse.candidates()).thenReturn(Optional.empty());
        
        when(mockModels.generateContent(anyString(), anyList(), any()))
            .thenReturn(blankResponse);

        // Act & Assert
        assertThatThrownBy(() -> geminiApiClient.callGeminiApi("test"))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("unavailable");
    }

    @Test
    @DisplayName("Response with empty candidates list triggers exception")
    void emptyCandidatesList_triggersException() {
        // Arrange: Response with empty candidates
        GenerateContentResponse emptyResponse = mock(GenerateContentResponse.class);
        when(emptyResponse.text()).thenReturn(null);
        when(emptyResponse.candidates()).thenReturn(Optional.of(Collections.emptyList()));
        
        when(mockModels.generateContent(anyString(), anyList(), any()))
            .thenReturn(emptyResponse);

        // Act & Assert
        assertThatThrownBy(() -> geminiApiClient.callGeminiApi("test"))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("unavailable");
    }

    @Test
    @DisplayName("Fallback extraction from candidates works")
    void fallbackExtraction_fromCandidates_succeeds() {
        // Arrange: Primary text() is null, but candidates have content
        GenerateContentResponse response = mock(GenerateContentResponse.class);
        when(response.text()).thenReturn(null);
        
        // Mock candidate structure
        Part mockPart = mock(Part.class);
        when(mockPart.text()).thenReturn(Optional.of("{\"dailyMetrics\": [], \"activities\": []}"));
        
        Content mockContent = mock(Content.class);
        when(mockContent.parts()).thenReturn(Optional.of(List.of(mockPart)));
        
        Candidate mockCandidate = mock(Candidate.class);
        when(mockCandidate.content()).thenReturn(Optional.of(mockContent));
        
        when(response.candidates()).thenReturn(Optional.of(List.of(mockCandidate)));
        
        when(mockModels.generateContent(anyString(), anyList(), any())).thenReturn(response);

        // Act
        String result = geminiApiClient.callGeminiApi("test");

        // Assert: Fallback extraction worked
        assertThat(result).isNotNull();
        assertThat(result).contains("dailyMetrics");
    }

    @Test
    @DisplayName("Candidate with null content triggers exception")
    void candidateWithNullContent_triggersException() {
        // Arrange
        GenerateContentResponse response = mock(GenerateContentResponse.class);
        when(response.text()).thenReturn(null);
        
        Candidate mockCandidate = mock(Candidate.class);
        when(mockCandidate.content()).thenReturn(null);
        
        when(response.candidates()).thenReturn(Optional.of(List.of(mockCandidate)));
        when(mockModels.generateContent(anyString(), anyList(), any())).thenReturn(response);

        // Act & Assert
        assertThatThrownBy(() -> geminiApiClient.callGeminiApi("test"))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("unavailable");
    }

    @Test
    @DisplayName("Candidate with empty parts triggers exception")
    void candidateWithEmptyParts_triggersException() {
        // Arrange
        GenerateContentResponse response = mock(GenerateContentResponse.class);
        when(response.text()).thenReturn(null);
        
        Content mockContent = mock(Content.class);
        when(mockContent.parts()).thenReturn(Optional.empty());
        
        Candidate mockCandidate = mock(Candidate.class);
        when(mockCandidate.content()).thenReturn(Optional.of(mockContent));
        
        when(response.candidates()).thenReturn(Optional.of(List.of(mockCandidate)));
        when(mockModels.generateContent(anyString(), anyList(), any())).thenReturn(response);

        // Act & Assert
        assertThatThrownBy(() -> geminiApiClient.callGeminiApi("test"))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("unavailable");
    }

    @Test
    @DisplayName("Part with empty text triggers exception")
    void partWithEmptyText_triggersException() {
        // Arrange
        GenerateContentResponse response = mock(GenerateContentResponse.class);
        when(response.text()).thenReturn(null);
        
        Part mockPart = mock(Part.class);
        when(mockPart.text()).thenReturn(Optional.of(""));
        
        Content mockContent = mock(Content.class);
        when(mockContent.parts()).thenReturn(Optional.of(List.of(mockPart)));
        
        Candidate mockCandidate = mock(Candidate.class);
        when(mockCandidate.content()).thenReturn(Optional.of(mockContent));
        
        when(response.candidates()).thenReturn(Optional.of(List.of(mockCandidate)));
        when(mockModels.generateContent(anyString(), anyList(), any())).thenReturn(response);

        // Act & Assert
        assertThatThrownBy(() -> geminiApiClient.callGeminiApi("test"))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("unavailable");
    }

    @Test
    @DisplayName("Multiple parts with first empty skips to second valid part")
    void multipleParts_firstEmpty_usesSecondPart() {
        // Arrange: First part empty, second part has valid JSON
        GenerateContentResponse response = mock(GenerateContentResponse.class);
        when(response.text()).thenReturn(null);
        
        Part emptyPart = mock(Part.class);
        when(emptyPart.text()).thenReturn(Optional.of(""));
        
        Part validPart = mock(Part.class);
        when(validPart.text()).thenReturn(Optional.of("{\"dailyMetrics\": [], \"activities\": []}"));
        
        Content mockContent = mock(Content.class);
        when(mockContent.parts()).thenReturn(Optional.of(List.of(emptyPart, validPart)));
        
        Candidate mockCandidate = mock(Candidate.class);
        when(mockCandidate.content()).thenReturn(Optional.of(mockContent));
        
        when(response.candidates()).thenReturn(Optional.of(List.of(mockCandidate)));
        when(mockModels.generateContent(anyString(), anyList(), any())).thenReturn(response);

        // Act
        String result = geminiApiClient.callGeminiApi("test");

        // Assert: Second part was used
        assertThat(result).isNotNull();
        assertThat(result).contains("dailyMetrics");
    }

    @Test
    @DisplayName("API throws generic exception triggers fallback")
    void apiThrowsException_triggersFallback() {
        // Arrange
        when(mockModels.generateContent(anyString(), anyList(), any()))
            .thenThrow(new RuntimeException("Network timeout"));

        // Act & Assert
        assertThatThrownBy(() -> geminiApiClient.callGeminiApi("test"))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("unavailable");
    }
}