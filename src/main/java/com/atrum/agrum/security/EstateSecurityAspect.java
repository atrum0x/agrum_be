package com.atrum.agrum.security;

import com.atrum.agrum.user.AppUserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.hibernate.Session;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Aspect
@Component
public class EstateSecurityAspect {

    @PersistenceContext
    private EntityManager entityManager;

    private final StringRedisTemplate redisTemplate;
    private final AppUserRepository userRepository;

    public EstateSecurityAspect(StringRedisTemplate redisTemplate, AppUserRepository userRepository) {
        this.redisTemplate = redisTemplate;
        this.userRepository = userRepository;
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

            // Step 4: Apply the Hibernate Row-Level Security Filter
            if (allowedEstates != null && !allowedEstates.isEmpty()) {
                Session session = entityManager.unwrap(Session.class);
                session.enableFilter("estateSecurityFilter")
                        .setParameterList("allowedEstates", allowedEstates);
            }
        }
    }
}