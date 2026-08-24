package com.atrum.agrum.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimitingService {

    // In-memory cache of buckets per user/IP.
    private final Map<String, Bucket> cache = new ConcurrentHashMap<>();

    public Bucket resolveBucket(String key, boolean isAuthenticated) {
        return cache.computeIfAbsent(key, k -> createNewBucket(isAuthenticated));
    }

    private Bucket createNewBucket(boolean isAuthenticated) {
        Bandwidth limit;
        if (isAuthenticated) {
            // Logged-in users: 100 requests per minute
            limit = Bandwidth.builder()
                    .capacity(100)
                    .refillGreedy(100, Duration.ofMinutes(1))
                    .build();

        } else {
            // Public routes (Login/Register): strict limit of 10 requests per minute per IP
            limit = Bandwidth.builder()
                    .capacity(10)
                    .refillGreedy(10, Duration.ofMinutes(1))
                    .build();

        }
        return Bucket.builder().addLimit(limit).build();
    }
}
