package org.shark.mentor.mcp.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Service for handling automatic translation of user questions to multiple languages
 * for querying MCP servers and ensuring consistent Spanish responses
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TranslationService {

    private final LlmServiceEnhanced llmService;

    /**
     * Translate user question to English if it's not already in English
     */
    public String translateToEnglish(String question) {
        if (isLikelyEnglish(question)) {
            log.debug("Question appears to be in English, no translation needed: {}", question);
            return question;
        }

        String prompt = "Translate the following text to English. Only return the translation, no explanations:\n\n" + question;
        
        try {
            String translation = llmService.generateWithMemory("translation_en", prompt, "");
            log.debug("Translated to English: {} -> {}", question, translation);
            return translation.trim();
        } catch (Exception e) {
            log.warn("Failed to translate to English, using original: {}", e.getMessage());
            return question;
        }
    }

    /**
     * Translate user question to Spanish if it's not already in Spanish
     */
    public String translateToSpanish(String question) {
        if (isLikelySpanish(question)) {
            log.debug("Question appears to be in Spanish, no translation needed: {}", question);
            return question;
        }

        String prompt = "Traduce el siguiente texto al español. Solo devuelve la traducción, sin explicaciones:\n\n" + question;
        
        try {
            String translation = llmService.generateWithMemory("translation_es", prompt, "");
            log.debug("Translated to Spanish: {} -> {}", question, translation);
            return translation.trim();
        } catch (Exception e) {
            log.warn("Failed to translate to Spanish, using original: {}", e.getMessage());
            return question;
        }
    }

    /**
     * Get both English and Spanish versions of a question for comprehensive MCP server querying
     */
    public TranslationResult getMultilingualVersions(String originalQuestion) {
        log.info("Creating multilingual versions for question: {}", originalQuestion);
        
        CompletableFuture<String> englishFuture = CompletableFuture.supplyAsync(() -> 
            translateToEnglish(originalQuestion));
        
        CompletableFuture<String> spanishFuture = CompletableFuture.supplyAsync(() -> 
            translateToSpanish(originalQuestion));
        
        try {
            String englishVersion = englishFuture.get();
            String spanishVersion = spanishFuture.get();
            
            return TranslationResult.builder()
                    .original(originalQuestion)
                    .english(englishVersion)
                    .spanish(spanishVersion)
                    .build();
        } catch (Exception e) {
            log.error("Error creating multilingual versions: {}", e.getMessage(), e);
            // Fallback to original question
            return TranslationResult.builder()
                    .original(originalQuestion)
                    .english(originalQuestion)
                    .spanish(originalQuestion)
                    .build();
        }
    }

    /**
     * Simple heuristic to detect if text is likely in English
     */
    private boolean isLikelyEnglish(String text) {
        if (text == null || text.trim().isEmpty()) {
            return true;
        }

        String lowerText = text.toLowerCase();
        
        // Common English words
        List<String> englishIndicators = Arrays.asList(
            "the", "and", "is", "are", "was", "were", "have", "has", "had", 
            "do", "does", "did", "will", "would", "can", "could", "should",
            "what", "where", "when", "why", "how", "who", "which",
            "get", "show", "find", "search", "list", "tell", "give"
        );

        long englishWords = englishIndicators.stream()
            .mapToLong(word -> countWordOccurrences(lowerText, word))
            .sum();

        // If we find several English indicator words, assume it's English
        return englishWords >= 2;
    }

    /**
     * Simple heuristic to detect if text is likely in Spanish
     */
    private boolean isLikelySpanish(String text) {
        if (text == null || text.trim().isEmpty()) {
            return false;
        }

        String lowerText = text.toLowerCase();
        
        // Common Spanish words and characteristics
        List<String> spanishIndicators = Arrays.asList(
            "el", "la", "los", "las", "un", "una", "es", "son", "está", "están",
            "de", "del", "con", "por", "para", "en", "que", "qué", "cómo",
            "cuál", "cuáles", "dónde", "cuándo", "quién", "quiénes",
            "buscar", "encontrar", "mostrar", "listar", "decir", "dar",
            "película", "películas", "año", "años", "nombre", "información"
        );

        long spanishWords = spanishIndicators.stream()
            .mapToLong(word -> countWordOccurrences(lowerText, word))
            .sum();

        // Check for Spanish-specific characters
        boolean hasSpanishChars = lowerText.contains("ñ") || 
                                 lowerText.contains("á") || lowerText.contains("é") || 
                                 lowerText.contains("í") || lowerText.contains("ó") || 
                                 lowerText.contains("ú") || lowerText.contains("ü");

        return spanishWords >= 2 || hasSpanishChars;
    }

    private long countWordOccurrences(String text, String word) {
        return Arrays.stream(text.split("\\s+"))
            .mapToLong(w -> w.equals(word) ? 1 : 0)
            .sum();
    }

    /**
     * Result object containing original question and translations
     */
    @lombok.Builder
    @lombok.Data
    public static class TranslationResult {
        private String original;
        private String english;
        private String spanish;
    }
}