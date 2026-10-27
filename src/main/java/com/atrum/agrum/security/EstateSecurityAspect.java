package com.atrum.agrum.security;

import com.atrum.agrum.user.AppUserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.hibernate.Session;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

@Aspect
@Component
public class EstateSecurityAspect {

    // Estate ids are never blank, so this matches no rows
    private static final String NO_ESTATE = "";
    // Comma-separated granted estate ids; "" means none
    public static final String CACHE_PREFIX = "userEstates::";
    private static final Duration CACHE_TTL = Duration.ofDays(1);

    @PersistenceContext
    private EntityManager entityManager;

    private final StringRedisTemplate redisTemplate;
    private final AppUserRepository userRepository;
    private final String superUsername;

    public EstateSecurityAspect(StringRedisTemplate redisTemplate, AppUserRepository userRepository,
                                @Value("${SUPERUSER_USERNAME}") String superUsername) {
        this.redisTemplate = redisTemplate;
        this.userRepository = userRepository;
        this.superUsername = superUsername;
    }

    @Before("@annotation(org.springframework.transaction.annotation.Transactional) || @within(org.springframework.transaction.annotation.Transactional)")
    public void enableEstateFilter() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return;
        }
        String username = auth.getName();
        // Only the superuser sees every estate
        if (superUsername.equals(username)) {
            return;
        }

        // The current grants: Redis, else the DB. The token's "estates" claim is not used, because it
        // only changes at the next refresh; PermissionCacheEvictor clears this cache when grants change
        List<String> allowedEstates = cachedEstates(username);
        if (allowedEstates == null) {
            allowedEstates = userRepository.findAllowedEstateIdsByUsername(username);
            cacheEstates(username, allowedEstates);
        }

        // Anyone with no granted estates sees none: "IN ()" is invalid SQL, so filter on an estate id
        // that cannot exist instead
        if (allowedEstates.isEmpty()) {
            allowedEstates = List.of(NO_ESTATE);
        }
        Session session = entityManager.unwrap(Session.class);
        session.enableFilter("estateSecurityFilter")
                .setParameterList("allowedEstates", allowedEstates);
    }

    private List<String> cachedEstates(String username) {
        try {
            String cached = redisTemplate.opsForValue().get(CACHE_PREFIX + username);
            if (cached != null) {
                return cached.isEmpty() ? List.of() : Arrays.asList(cached.split(","));
            }
        } catch (Exception e) {
            System.err.println("Valkey/Redis down during estate check! Falling back to DB.");
        }
        return null;
    }

    private void cacheEstates(String username, List<String> estates) {
        try {
            redisTemplate.opsForValue().set(CACHE_PREFIX + username, String.join(",", estates), CACHE_TTL);
        } catch (Exception e) {
            // The DB answer is used without caching
        }
    }
}
