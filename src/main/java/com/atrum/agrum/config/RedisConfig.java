package com.atrum.agrum.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer; // <-- Import the base interface

import java.time.Duration;

@Configuration
public class RedisConfig {

    @Bean
    public RedisCacheConfiguration cacheConfiguration() {
        return RedisCacheConfiguration.defaultCacheConfig()
                // 1. Set cache expiration to 60 minutes
                .entryTtl(Duration.ofMinutes(60))

                // 2. Disable caching null values
                .disableCachingNullValues()

                // 3. Use the built-in static factory method for JSON serialization
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(RedisSerializer.json())
                );
    }
}