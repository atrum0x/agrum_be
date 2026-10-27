package com.atrum.agrum.security;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

/**
 * Clears the cached "userPermissions" entries (key {@code username_METHOD}, see
 * PermissionSetRepository) when grants change, and the cached site list ({@code userEstates::username},
 * read by EstateSecurityAspect) when site grants change, so a change takes effect on the next request
 * instead of when the cache entry expires.
 *
 * <p>Evicts after the transaction commits: evicting earlier would let a request in between cache
 * the old grants again. Like every Redis use here, a failure is logged and ignored.
 */
@Component
@RequiredArgsConstructor
public class PermissionCacheEvictor {

    private static final Logger log = LoggerFactory.getLogger(PermissionCacheEvictor.class);
    private static final String CACHE = "userPermissions";
    private static final List<String> METHODS = List.of("GET", "POST", "PUT", "PATCH", "DELETE");

    private final CacheManager cacheManager;
    private final StringRedisTemplate redisTemplate;

    /** A user's grants changed (permission sets granted or revoked, account deleted). */
    public void evictUsers(Collection<String> usernames) {
        afterCommit(() -> withCache(cache -> usernames.forEach(username ->
                METHODS.forEach(method -> cache.evict(username + "_" + method)))));
    }

    public void evictUser(String username) {
        evictUsers(List.of(username));
    }

    /** The sites granted to a user changed. */
    public void evictUserEstates(String username) {
        afterCommit(() -> {
            try {
                redisTemplate.delete(EstateSecurityAspect.CACHE_PREFIX + username);
            } catch (RuntimeException e) {
                log.warn("Could not evict the site list of {}; it may stay stale until it expires. Reason: {}",
                        username, e.getMessage());
            }
        });
    }

    /** A permission set's endpoints changed: every user holding it is affected. */
    public void evictAll() {
        afterCommit(() -> withCache(Cache::clear));
    }

    private void withCache(Consumer<Cache> action) {
        try {
            Cache cache = cacheManager.getCache(CACHE);
            if (cache != null) {
                action.accept(cache);
            }
        } catch (RuntimeException e) {
            log.warn("Could not evict {} cache; grants may stay stale until it expires. Reason: {}", CACHE, e.getMessage());
        }
    }

    private static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
