package com.atrum.agrum.auth;

import com.atrum.agrum.auth.AuthDto.TokenResponse;
import com.atrum.agrum.exception.InvalidCredentialsException;
import com.atrum.agrum.user.AppUser;
import com.atrum.agrum.user.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Fingerprint login: the phone's Keystore key signs a one-time challenge and the server checks it
 * against the public key saved at set-up. A real EC P-256 key stands in for the phone's key.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BiometricLoginServiceTest {

    @Mock
    private BiometricKeyRepository keyRepository;
    @Mock
    private AppUserRepository userRepository;
    @Mock
    private AuthService authService;

    private final Map<String, BiometricKey> table = new HashMap<>();
    private Instant now = Instant.parse("2026-10-30T06:00:00Z");
    private BiometricLoginService service;
    private KeyPair phoneKey;
    private AppUser sup;

    @BeforeEach
    void setUp() throws Exception {
        Clock clock = new Clock() {
            @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(java.time.ZoneId zone) { return this; }
            @Override public Instant instant() { return now; }
        };
        service = new BiometricLoginService(keyRepository, userRepository, authService, clock);

        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        phoneKey = generator.generateKeyPair();

        sup = new AppUser();
        sup.setUsername("SUP01");
        when(authService.authenticate("sup01", "pw")).thenReturn(sup);
        when(authService.authenticate(anyString(), eq("bad")))
                .thenThrow(new InvalidCredentialsException("Username or password is incorrect."));
        when(authService.issueTokens(eq(sup), any())).thenReturn(new TokenResponse("jwt", "refresh"));
        when(userRepository.findById("SUP01")).thenReturn(Optional.of(sup));

        // The table is copied in and out, as JPA would: a saved entity is not the stored row
        when(keyRepository.save(any())).thenAnswer(inv -> {
            BiometricKey key = inv.getArgument(0);
            table.put(key.getId(), copy(key));
            return key;
        });
        when(keyRepository.findById(anyString()))
                .thenAnswer(inv -> Optional.ofNullable(table.get(inv.<String>getArgument(0))).map(this::copy));
        when(keyRepository.consumeChallenge(anyString(), anyString())).thenAnswer(inv -> {
            BiometricKey key = table.get(inv.<String>getArgument(0));
            if (key == null || !inv.getArgument(1).equals(key.getChallenge())) return 0;
            key.setChallenge(null, null);
            return 1;
        });
        doAnswer(inv -> table.remove(inv.<String>getArgument(0))).when(keyRepository).deleteById(anyString());
    }

    private BiometricKey copy(BiometricKey key) {
        BiometricKey copy = new BiometricKey(key.getId(), key.getUsername(), key.getPublicKey(), key.getCreatedAt());
        copy.setChallenge(key.getChallenge(), key.getChallengeExpiresAt());
        copy.setLastUsedAt(key.getLastUsedAt());
        return copy;
    }

    private String publicKey() {
        return Base64.getEncoder().encodeToString(phoneKey.getPublic().getEncoded());
    }

    private String sign(String text) throws Exception {
        Signature signer = Signature.getInstance("SHA256withECDSA");
        signer.initSign(phoneKey.getPrivate());
        signer.update(text.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(signer.sign());
    }

    private String register() {
        return service.register("sup01", "pw", publicKey());
    }

    @Test
    void setUpThenLogInWithASignedChallenge() throws Exception {
        String keyId = register();
        assertThat(table.get(keyId).getUsername()).isEqualTo("SUP01");

        String challenge = service.challenge(keyId);
        TokenResponse tokens = service.login(keyId, sign(BiometricLoginService.signedText(keyId, challenge)));

        assertThat(tokens.getAccessToken()).isEqualTo("jwt");
        assertThat(table.get(keyId).getLastUsedAt()).isEqualTo(now);
        assertThat(table.get(keyId).getChallenge()).isNull();
    }

    @Test
    void setUpNeedsTheRightPassword() {
        assertThatThrownBy(() -> service.register("sup01", "bad", publicKey()))
                .isInstanceOf(InvalidCredentialsException.class);
        assertThat(table).isEmpty();
    }

    @Test
    void setUpRefusesAKeyThatIsNotEcP256() throws Exception {
        KeyPairGenerator rsa = KeyPairGenerator.getInstance("RSA");
        rsa.initialize(2048);
        String rsaKey = Base64.getEncoder().encodeToString(rsa.generateKeyPair().getPublic().getEncoded());

        assertThatThrownBy(() -> service.register("sup01", "pw", rsaKey)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.register("sup01", "pw", "not a key")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aSignatureWorksOnlyOnce() throws Exception {
        String keyId = register();
        String challenge = service.challenge(keyId);
        String signature = sign(BiometricLoginService.signedText(keyId, challenge));
        service.login(keyId, signature);

        assertThatThrownBy(() -> service.login(keyId, signature))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage(BiometricLoginService.FAILED);
    }

    @Test
    void aWrongSignatureFailsAndUsesUpTheChallenge() throws Exception {
        String keyId = register();
        String challenge = service.challenge(keyId);

        assertThatThrownBy(() -> service.login(keyId, sign("agrum-login:" + keyId + ":other")))
                .isInstanceOf(InvalidCredentialsException.class);
        // The right signature can't be tried afterwards against the same challenge
        assertThatThrownBy(() -> service.login(keyId, sign(BiometricLoginService.signedText(keyId, challenge))))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(authService, never()).issueTokens(any(), any());
    }

    @Test
    void anotherKeysSignatureIsRefused() throws Exception {
        String keyId = register();
        String challenge = service.challenge(keyId);
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        phoneKey = generator.generateKeyPair();

        assertThatThrownBy(() -> service.login(keyId, sign(BiometricLoginService.signedText(keyId, challenge))))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void anExpiredChallengeIsRefused() throws Exception {
        String keyId = register();
        String challenge = service.challenge(keyId);
        now = now.plus(BiometricLoginService.CHALLENGE_LIFETIME);

        assertThatThrownBy(() -> service.login(keyId, sign(BiometricLoginService.signedText(keyId, challenge))))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void aNewChallengeReplacesTheOldOne() throws Exception {
        String keyId = register();
        String first = service.challenge(keyId);
        service.challenge(keyId);

        assertThatThrownBy(() -> service.login(keyId, sign(BiometricLoginService.signedText(keyId, first))))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void aRemovedOrUnknownKeyIsNotSetUp() {
        String keyId = register();
        service.remove(keyId);

        assertThatThrownBy(() -> service.challenge(keyId))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage(BiometricLoginService.NOT_SET_UP);
        assertThatThrownBy(() -> service.login(null, "x"))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage(BiometricLoginService.NOT_SET_UP);
    }

    @Test
    void LG04_anInactiveUserIsStillRefused() throws Exception {
        String keyId = register();
        String challenge = service.challenge(keyId);
        when(authService.issueTokens(eq(sup), any()))
                .thenThrow(new InvalidCredentialsException("Your account is not active. Please contact the administrator."));

        assertThatThrownBy(() -> service.login(keyId, sign(BiometricLoginService.signedText(keyId, challenge))))
                .hasMessage("Your account is not active. Please contact the administrator.");
    }
}
