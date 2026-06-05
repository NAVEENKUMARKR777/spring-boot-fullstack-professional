package com.example.demo.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;

/**
 * LF-202: When Redis is down, Spring's @Cacheable/@CacheEvict annotations
 * would normally throw and kill the request. This handler logs the failure
 * and silently falls through to the real method (DB path).
 * Cache resumes automatically when Redis comes back — no restart needed.
 */
@Slf4j
public class ResilientCacheErrorHandler implements CacheErrorHandler {

    @Override
    public void handleCacheGetError(RuntimeException ex, Cache cache, Object key) {
        log.warn("Cache GET failed on '{}' key='{}': {} — falling back to DB",
                cache.getName(), key, ex.getMessage());
    }

    @Override
    public void handleCachePutError(RuntimeException ex, Cache cache, Object key, Object value) {
        log.warn("Cache PUT failed on '{}' key='{}': {} — data served from DB only",
                cache.getName(), key, ex.getMessage());
    }

    @Override
    public void handleCacheEvictError(RuntimeException ex, Cache cache, Object key) {
        log.warn("Cache EVICT failed on '{}' key='{}': {}",
                cache.getName(), key, ex.getMessage());
    }

    @Override
    public void handleCacheClearError(RuntimeException ex, Cache cache) {
        log.warn("Cache CLEAR failed on '{}': {}", cache.getName(), ex.getMessage());
    }
}
