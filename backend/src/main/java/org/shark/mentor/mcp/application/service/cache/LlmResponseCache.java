package org.shark.mentor.mcp.application.service.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Cache for LLM responses to avoid repeated calls for identical queries
 */
@Slf4j
@Service
public class LlmResponseCache {
    
    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleanupExecutor = Executors.newSingleThreadScheduledExecutor();
    private final long ttlMillis;
    private final int maxCacheSize;
    
    public LlmResponseCache() {
        this.ttlMillis = TimeUnit.MINUTES.toMillis(10); // 10 minutes TTL
        this.maxCacheSize = 1000; // Maximum cached responses
        
        // Schedule periodic cleanup
        cleanupExecutor.scheduleAtFixedRate(this::cleanup, 1, 1, TimeUnit.MINUTES);
        log.info("LlmResponseCache initialized with TTL: {}ms, max size: {}", ttlMillis, maxCacheSize);
    }
    
    public String get(String question, String context) {
        String key = generateKey(question, context);
        CacheEntry entry = cache.get(key);
        
        if (entry != null && !entry.isExpired()) {
            log.debug("Cache hit for key: {}", key);
            return entry.response;
        }
        
        if (entry != null && entry.isExpired()) {
            cache.remove(key);
            log.debug("Removed expired cache entry for key: {}", key);
        }
        
        return null;
    }
    
    public void put(String question, String context, String response) {
        if (response == null || response.trim().isEmpty()) {
            return;
        }
        
        // Enforce cache size limit
        if (cache.size() >= maxCacheSize) {
            // Remove oldest entries (simple LRU-like behavior)
            cache.entrySet().removeIf(entry -> entry.getValue().isExpired());
            
            // If still at limit, remove some more entries
            if (cache.size() >= maxCacheSize) {
                cache.entrySet().stream()
                    .limit(maxCacheSize / 4) // Remove 25% of entries
                    .forEach(entry -> cache.remove(entry.getKey()));
            }
        }
        
        String key = generateKey(question, context);
        cache.put(key, new CacheEntry(response, System.currentTimeMillis() + ttlMillis));
        log.debug("Cached response for key: {}", key);
    }
    
    public void invalidate(String question, String context) {
        String key = generateKey(question, context);
        cache.remove(key);
        log.debug("Invalidated cache entry for key: {}", key);
    }
    
    public void clear() {
        cache.clear();
        log.info("Cache cleared");
    }
    
    public int size() {
        return cache.size();
    }
    
    private String generateKey(String question, String context) {
        // Create a deterministic key based on question and context
        String combined = (question != null ? question.trim() : "") + 
                         "|" + 
                         (context != null ? context.trim() : "");
        return Integer.toString(combined.hashCode());
    }
    
    private void cleanup() {
        int initialSize = cache.size();
        cache.entrySet().removeIf(entry -> entry.getValue().isExpired());
        int finalSize = cache.size();
        
        if (initialSize != finalSize) {
            log.debug("Cache cleanup: removed {} expired entries, {} remaining", 
                     initialSize - finalSize, finalSize);
        }
    }
    
    private static class CacheEntry {
        final String response;
        final long expirationTime;
        
        CacheEntry(String response, long expirationTime) {
            this.response = response;
            this.expirationTime = expirationTime;
        }
        
        boolean isExpired() {
            return System.currentTimeMillis() > expirationTime;
        }
    }
}