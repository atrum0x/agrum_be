package com.atrum.agrum.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ResilientCacheConfig implements CachingConfigurer {

    private static final Logger logger = LoggerFactory.getLogger(ResilientCacheConfig.class);

    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                logger.warn("Redis GET failed for key {}. Falling back to PostgreSQL. Reason: {}", key, exception.getMessage());
                // By not throwing an exception here, Spring proceeds to execute the database query!
            }

            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                logger.warn("Redis PUT failed for key {}. Skipping cache update.", key);
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                logger.warn("Redis EVICT failed for key {}. Cache may be stale when it recovers.", key);
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                logger.warn("Redis CLEAR failed. Cache may be stale.");
            }
        };
    }
}