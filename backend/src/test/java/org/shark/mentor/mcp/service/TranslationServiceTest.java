package org.shark.mentor.mcp.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test for TranslationService multilingual functionality
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TranslationServiceTest {

    @Mock
    private LlmServiceEnhanced llmServiceEnhanced;

    private TranslationService translationService;

    @BeforeEach
    void setUp() {
        translationService = new TranslationService(llmServiceEnhanced);
    }

    @Test
    void testTranslateToEnglish_AlreadyEnglish() {
        // Given
        String englishQuestion = "What movies are available?";

        // When
        String result = translationService.translateToEnglish(englishQuestion);

        // Then
        assertEquals(englishQuestion, result);
        verifyNoInteractions(llmServiceEnhanced);
    }

    @Test
    void testTranslateToEnglish_SpanishInput() {
        // Given
        String spanishQuestion = "¿Qué películas están disponibles?";
        String expectedTranslation = "What movies are available?";
        
        when(llmServiceEnhanced.generateWithMemory(eq("translation_en"), 
                anyString(), eq("")))
                .thenReturn(expectedTranslation);

        // When
        String result = translationService.translateToEnglish(spanishQuestion);

        // Then
        assertEquals(expectedTranslation, result);
        verify(llmServiceEnhanced).generateWithMemory(eq("translation_en"), 
                anyString(), eq(""));
    }

    @Test
    void testTranslateToSpanish_AlreadySpanish() {
        // Given
        String spanishQuestion = "¿Qué películas están disponibles?";

        // When
        String result = translationService.translateToSpanish(spanishQuestion);

        // Then
        assertEquals(spanishQuestion, result);
        verifyNoInteractions(llmServiceEnhanced);
    }

    @Test
    void testTranslateToSpanish_EnglishInput() {
        // Given
        String englishQuestion = "What movies are available?";
        String expectedTranslation = "¿Qué películas están disponibles?";
        
        when(llmServiceEnhanced.generateWithMemory(eq("translation_es"), 
                anyString(), eq("")))
                .thenReturn(expectedTranslation);

        // When
        String result = translationService.translateToSpanish(englishQuestion);

        // Then
        assertEquals(expectedTranslation, result);
        verify(llmServiceEnhanced).generateWithMemory(eq("translation_es"), 
                anyString(), eq(""));
    }

    @Test
    void testGetMultilingualVersions_EnglishInput() {
        // Given
        String englishQuestion = "Show me action movies";
        String spanishTranslation = "Muéstrame películas de acción";
        
        when(llmServiceEnhanced.generateWithMemory(eq("translation_es"), 
                anyString(), eq("")))
                .thenReturn(spanishTranslation);

        // When
        TranslationService.TranslationResult result = translationService.getMultilingualVersions(englishQuestion);

        // Then
        assertNotNull(result);
        assertEquals(englishQuestion, result.getOriginal());
        assertEquals(englishQuestion, result.getEnglish()); // Should remain the same
        assertEquals(spanishTranslation, result.getSpanish());
    }

    @Test
    void testGetMultilingualVersions_SpanishInput() {
        // Given
        String spanishQuestion = "Muéstrame películas de acción";
        String englishTranslation = "Show me action movies";
        
        when(llmServiceEnhanced.generateWithMemory(eq("translation_en"), 
                anyString(), eq("")))
                .thenReturn(englishTranslation);

        // When
        TranslationService.TranslationResult result = translationService.getMultilingualVersions(spanishQuestion);

        // Then
        assertNotNull(result);
        assertEquals(spanishQuestion, result.getOriginal());
        assertEquals(englishTranslation, result.getEnglish());
        assertEquals(spanishQuestion, result.getSpanish()); // Should remain the same
    }

    @Test
    void testGetMultilingualVersions_TranslationError() {
        // Given
        String question = "Some question";
        
        when(llmServiceEnhanced.generateWithMemory(anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("Translation service unavailable"));

        // When
        TranslationService.TranslationResult result = translationService.getMultilingualVersions(question);

        // Then - should fallback to original question for all languages
        assertNotNull(result);
        assertEquals(question, result.getOriginal());
        assertEquals(question, result.getEnglish());
        assertEquals(question, result.getSpanish());
    }

    @Test
    void testLanguageDetection_EnglishWords() {
        // Given
        String englishText = "What is the best movie?";

        // When
        String englishResult = translationService.translateToEnglish(englishText);
        
        // Then - should not translate since it's detected as English
        assertEquals(englishText, englishResult);
        verifyNoInteractions(llmServiceEnhanced);
    }

    @Test
    void testLanguageDetection_SpanishWords() {
        // Given
        String spanishText = "¿Cuál es la mejor película?";

        // When
        String spanishResult = translationService.translateToSpanish(spanishText);
        
        // Then - should not translate since it's detected as Spanish
        assertEquals(spanishText, spanishResult);
        verifyNoInteractions(llmServiceEnhanced);
    }

    @Test
    void testLanguageDetection_SpanishCharacters() {
        // Given
        String spanishTextWithAccents = "Información sobre películas";

        // When
        String spanishResult = translationService.translateToSpanish(spanishTextWithAccents);
        
        // Then - should not translate since it contains Spanish characters
        assertEquals(spanishTextWithAccents, spanishResult);
        verifyNoInteractions(llmServiceEnhanced);
    }
}