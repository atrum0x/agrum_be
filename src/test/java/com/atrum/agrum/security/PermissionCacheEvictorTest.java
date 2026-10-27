package com.atrum.agrum.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PermissionCacheEvictorTest {

    private CacheManager cacheManager;
    private Cache cache;
    private PermissionCacheEvictor evictor;

    @BeforeEach
    void setUp() {
        cacheManager = new ConcurrentMapCacheManager("userPermissions");
        cache = cacheManager.getCache("userPermissions");
        evictor = new PermissionCacheEvictor(cacheManager);
        cache.put("SUP01_GET", List.of("/api/attendance"));
        cache.put("SUP01_POST", List.of("/api/attendance/sync"));
        cache.put("SUP01_PATCH", List.of("/api/x"));
        cache.put("SUP02_GET", List.of("/api/attendance"));
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void evictsEveryMethodOfTheUserOnly() {
        evictor.evictUser("SUP01");

        assertThat(cache.get("SUP01_GET")).isNull();
        assertThat(cache.get("SUP01_POST")).isNull();
        assertThat(cache.get("SUP01_PATCH")).isNull();
        assertThat(cache.get("SUP02_GET")).isNotNull();
    }

    @Test
    void evictAllClearsEveryUser() {
        evictor.evictAll();

        assertThat(cache.get("SUP01_GET")).isNull();
        assertThat(cache.get("SUP02_GET")).isNull();
    }

    @Test
    void insideATransactionEvictsOnlyAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();

        evictor.evictUsers(List.of("SUP01", "SUP02"));
        // A request before the commit would still read the old grants from the DB anyway
        assertThat(cache.get("SUP01_GET")).isNotNull();

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        assertThat(cache.get("SUP01_GET")).isNull();
        assertThat(cache.get("SUP02_GET")).isNull();
    }

    @Test
    void aCacheFailureDoesNotFailTheGrantChange() {
        CacheManager broken = mock(CacheManager.class);
        when(broken.getCache("userPermissions")).thenThrow(new IllegalStateException("Redis down"));

        new PermissionCacheEvictor(broken).evictAll();
    }
}
