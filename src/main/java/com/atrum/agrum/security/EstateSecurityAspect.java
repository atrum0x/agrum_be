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

import java.util.Arrays;
import java.util.List;

@Aspect
@Component
public class EstateSecurityAspect {

    // Estate ids are never blank, so this matches no rows
    private static final String NO_ESTATE = "";

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

        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            String username = auth.getName();
            List<String> allowedEstates = null;

            // Step 1: Fast Path (0ms) -> Read from SecurityContext details (JWT)
            if (auth.getDetails() instanceof List<?>) {
                allowedEstates = (List<String>) auth.getDetails();
            }

            // Step 2: Fallback to Valkey / Redis if missing in context
            if (allowedEstates == null || allowedEstates.isEmpty()) {
                try {
                    String cachedEstates = redisTemplate.opsForValue().get("userEstates::" + username);
                    if (cachedEstates != null && !cachedEstates.isBlank()) {
                        allowedEstates = Arrays.asList(cachedEstates.split(","));
                    }
                } catch (Exception e) {
                    System.err.println("Valkey/Redis down during estate check! Falling back to DB.");
                }
            }

            // Step 3: Final Fallback -> Database
            if (allowedEstates == null || allowedEstates.isEmpty()) {
                allowedEstates = userRepository.findAllowedEstateIdsByUsername(username);
            }

            // Step 4: Apply the Hibernate Row-Level Security Filter.
            // Only the superuser sees every estate. Anyone else with no granted estates sees none:
            // "IN ()" is invalid SQL, so filter on an estate id that cannot exist instead.
            if (superUsername.equals(username)) {
                return;
            }
            if (allowedEstates == null || allowedEstates.isEmpty()) {
                allowedEstates = List.of(NO_ESTATE);
            }
            Session session = entityManager.unwrap(Session.class);
            session.enableFilter("estateSecurityFilter")
                    .setParameterList("allowedEstates", allowedEstates);
        }
    }
}