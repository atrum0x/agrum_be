package com.atrum.agrum.auth;

import com.atrum.agrum.auth.AuthDto.LoginRequest;
import com.atrum.agrum.auth.AuthDto.TokenResponse;
import com.atrum.agrum.exception.InvalidCredentialsException;
import com.atrum.agrum.permission.PermissionSet;
import com.atrum.agrum.security.JwtTokenProvider;
import com.atrum.agrum.security.PermissionCacheEvictor;
import com.atrum.agrum.security.PermissionChecker;
import com.atrum.agrum.user.AppUser;
import com.atrum.agrum.user.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * One refresh token per login session: a web login doesn't end the phone's session, and the other
 * way round. Redis is down in these tests, so every check goes to the (in-memory) table.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceSessionsTest {

    @Mock
    private AppUserRepository userRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private BiometricKeyRepository biometricKeyRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtTokenProvider tokenProvider;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private PermissionChecker permissionChecker;
    @Mock
    private PermissionCacheEvictor permissionCacheEvictor;

    private static final Duration WEB = Duration.ofMinutes(30);

    private final Map<String, RefreshToken> table = new HashMap<>();
    private AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(userRepository, refreshTokenRepository, biometricKeyRepository, passwordEncoder, tokenProvider,
                redisTemplate, permissionChecker, permissionCacheEvictor);
        ReflectionTestUtils.setField(service, "superUsername", "admin");

        AppUser sup = new AppUser();
        sup.setUsername("SUP01");
        sup.setPassword("hash");
        sup.addPermissionSet(new PermissionSet());
        AppUser other = new AppUser();
        other.setUsername("SUP02");
        other.setPassword("hash");
        other.addPermissionSet(new PermissionSet());
        when(userRepository.findById("SUP01")).thenReturn(Optional.of(sup));
        when(userRepository.findById("SUP02")).thenReturn(Optional.of(other));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
        when(tokenProvider.generateToken(anyString(), any())).thenReturn("jwt");
        when(redisTemplate.opsForValue()).thenThrow(new IllegalStateException("Redis down"));
        when(redisTemplate.delete(anyString())).thenThrow(new IllegalStateException("Redis down"));

        when(refreshTokenRepository.save(any())).thenAnswer(inv -> {
            RefreshToken token = inv.getArgument(0);
            table.put(token.getTokenHash(), token);
            return token;
        });
        when(refreshTokenRepository.findById(anyString()))
                .thenAnswer(inv -> Optional.ofNullable(table.get(inv.<String>getArgument(0))));
        doAnswer(inv -> table.remove(inv.<String>getArgument(0))).when(refreshTokenRepository).deleteById(anyString());
        when(refreshTokenRepository.findAllByUsername(anyString())).thenAnswer(inv ->
                table.values().stream().filter(t -> t.getUsername().equals(inv.getArgument(0))).toList());
        doAnswer(inv -> table.values().removeIf(t -> t.getUsername().equals(inv.getArgument(0))))
                .when(refreshTokenRepository).deleteAllByUsername(anyString());
    }

    private TokenResponse login(String username) {
        LoginRequest request = new LoginRequest();
        ReflectionTestUtils.setField(request, "username", username);
        ReflectionTestUtils.setField(request, "password", "pw");
        return service.login(request);
    }

    @Test
    void aSecondLoginKeepsTheFirstSessionWorking() {
        TokenResponse phone = login("SUP01");
        TokenResponse web = login("SUP01");

        TokenResponse phoneRefreshed = service.refresh("SUP01", phone.getRefreshToken());
        TokenResponse webRefreshed = service.refreshByToken(web.getRefreshToken(), WEB);

        assertThat(phoneRefreshed.getRefreshToken()).isNotEqualTo(phone.getRefreshToken());
        assertThat(webRefreshed.getRefreshToken()).isNotEqualTo(web.getRefreshToken());
        assertThat(table).hasSize(2);
    }

    @Test
    void LG06_aWebSessionLivesOnlyTheIdlePeriodAndEachRefreshStartsItAgain() {
        LoginRequest request = new LoginRequest();
        ReflectionTestUtils.setField(request, "username", "SUP01");
        ReflectionTestUtils.setField(request, "password", "pw");

        TokenResponse web = service.login(request, WEB);
        Instant expiry = table.get(AuthService.hash(web.getRefreshToken())).getExpiryDate();
        assertThat(expiry).isBetween(Instant.now().plus(WEB).minusSeconds(5), Instant.now().plus(WEB));

        TokenResponse refreshed = service.refreshByToken(web.getRefreshToken(), WEB);
        assertThat(table.get(AuthService.hash(refreshed.getRefreshToken())).getExpiryDate())
                .isBetween(Instant.now().plus(WEB).minusSeconds(5), Instant.now().plus(WEB));

        // The phone keeps its long session
        TokenResponse phone = login("SUP01");
        assertThat(table.get(AuthService.hash(phone.getRefreshToken())).getExpiryDate())
                .isAfter(Instant.now().plus(Duration.ofDays(59)));
    }

    @Test
    void loginAndRefreshAreNotCaseSensitive() {
        Optional<AppUser> sup01 = userRepository.findById("SUP01");
        when(userRepository.findById("sup01")).thenReturn(Optional.empty());
        when(userRepository.findFirstByUsernameIgnoreCase("sup01")).thenReturn(sup01);

        TokenResponse session = login("sup01");

        assertThat(table.values()).extracting(RefreshToken::getUsername).containsExactly("SUP01");
        assertThat(service.refresh("sup01", session.getRefreshToken())).isNotNull();
    }

    @Test
    void aRotatedTokenCannotBeUsedAgain() {
        TokenResponse session = login("SUP01");
        service.refresh("SUP01", session.getRefreshToken());

        assertThatThrownBy(() -> service.refresh("SUP01", session.getRefreshToken()))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void anotherUsersTokenIsRefused() {
        TokenResponse session = login("SUP01");

        assertThatThrownBy(() -> service.refresh("SUP02", session.getRefreshToken()))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void anExpiredSessionIsRefused() {
        TokenResponse session = login("SUP01");
        String hash = AuthService.hash(session.getRefreshToken());
        table.put(hash, new RefreshToken(hash, "SUP01", Instant.now().minusSeconds(1)));

        assertThatThrownBy(() -> service.refreshByToken(session.getRefreshToken(), WEB))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void logoutEndsOnlyThatSession() {
        TokenResponse phone = login("SUP01");
        TokenResponse web = login("SUP01");

        service.logout(web.getRefreshToken());

        assertThatThrownBy(() -> service.refreshByToken(web.getRefreshToken(), WEB))
                .isInstanceOf(InvalidCredentialsException.class);
        assertThat(service.refresh("SUP01", phone.getRefreshToken())).isNotNull();
        verify(permissionCacheEvictor).evictUser("SUP01");
    }

    @Test
    void logoutWithoutATokenEndsNothing() {
        TokenResponse session = login("SUP01");

        service.logout(null);
        service.logout(" ");

        assertThat(service.refresh("SUP01", session.getRefreshToken())).isNotNull();
    }

    @Test
    void revokeSessionsEndsEverySessionOfTheUser() {
        TokenResponse phone = login("SUP01");
        TokenResponse web = login("SUP01");
        TokenResponse otherUser = login("SUP02");

        service.revokeSessions("SUP01");

        for (TokenResponse session : List.of(phone, web)) {
            assertThatThrownBy(() -> service.refreshByToken(session.getRefreshToken(), WEB))
                    .isInstanceOf(InvalidCredentialsException.class);
        }
        assertThat(service.refreshByToken(otherUser.getRefreshToken(), WEB)).isNotNull();
        // A password change also ends fingerprint login on the user's phones
        verify(biometricKeyRepository).deleteAllByUsername("SUP01");
    }

    @Test
    void onlyAHashOfTheTokenIsStored() {
        TokenResponse session = login("SUP01");

        assertThat(table).containsOnlyKeys(AuthService.hash(session.getRefreshToken()));
        assertThat(table.keySet()).doesNotContain(session.getRefreshToken());
    }
}
