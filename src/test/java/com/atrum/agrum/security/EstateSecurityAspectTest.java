package com.atrum.agrum.security;

import com.atrum.agrum.user.AppUserRepository;
import jakarta.persistence.EntityManager;
import org.hibernate.Filter;
import org.hibernate.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The estate filter uses the user's current site grants (Redis, else the DB), not the list in their
 * token, so a granted or removed site applies on the next request.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EstateSecurityAspectTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> values;
    @Mock
    private AppUserRepository userRepository;
    @Mock
    private EntityManager entityManager;
    @Mock
    private Session session;
    @Mock
    private Filter filter;

    private EstateSecurityAspect aspect;

    @BeforeEach
    void setUp() {
        aspect = new EstateSecurityAspect(redisTemplate, userRepository, "admin");
        ReflectionTestUtils.setField(aspect, "entityManager", entityManager);
        when(entityManager.unwrap(Session.class)).thenReturn(session);
        when(session.enableFilter("estateSecurityFilter")).thenReturn(filter);
        when(redisTemplate.opsForValue()).thenReturn(values);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void loggedInAs(String username, List<String> estatesInToken) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(username, null, List.of());
        auth.setDetails(estatesInToken);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void usesTheCachedGrantsNotTheOldListInTheToken() {
        loggedInAs("SUP01", List.of("S01", "S02"));
        // S02 was revoked after the token was issued
        when(values.get("userEstates::SUP01")).thenReturn("S01");

        aspect.enableEstateFilter();

        verify(filter).setParameterList("allowedEstates", List.of("S01"));
    }

    @Test
    void afterAGrantChangeReadsTheDatabaseAndCachesIt() {
        loggedInAs("SUP01", List.of("S01"));
        when(values.get("userEstates::SUP01")).thenReturn(null);
        when(userRepository.findAllowedEstateIdsByUsername("SUP01")).thenReturn(List.of("S01", "S03"));

        aspect.enableEstateFilter();

        verify(filter).setParameterList("allowedEstates", List.of("S01", "S03"));
        verify(values).set("userEstates::SUP01", "S01,S03", Duration.ofDays(1));
    }

    @Test
    void noGrantedSitesSeesNothing() {
        loggedInAs("SUP01", List.of("S01"));
        when(values.get("userEstates::SUP01")).thenReturn("");

        aspect.enableEstateFilter();

        verify(filter).setParameterList("allowedEstates", List.of(""));
        verify(userRepository, never()).findAllowedEstateIdsByUsername(anyString());
    }

    @Test
    void redisDownFallsBackToTheDatabase() {
        loggedInAs("SUP01", List.of());
        when(redisTemplate.opsForValue()).thenThrow(new IllegalStateException("Redis down"));
        when(userRepository.findAllowedEstateIdsByUsername("SUP01")).thenReturn(List.of("S02"));

        aspect.enableEstateFilter();

        verify(filter).setParameterList("allowedEstates", List.of("S02"));
    }

    @Test
    void theSuperuserIsNotFiltered() {
        loggedInAs("admin", List.of());

        aspect.enableEstateFilter();

        verify(session, never()).enableFilter(anyString());
        verify(filter, never()).setParameterList(anyString(), anyCollection());
        verify(values, never()).get(any());
    }
}
