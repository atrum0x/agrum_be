package com.atrum.agrum.auth;

import com.atrum.agrum.auth.AuthDto.TokenResponse;
import com.atrum.agrum.exception.InvalidCredentialsException;
import com.atrum.agrum.user.AppUser;
import com.atrum.agrum.user.AppUserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Fingerprint login for the phone, challenge-response like FIDO/passkeys. The phone makes an EC P-256
 * key in its Keystore that signs only after a fingerprint check and is destroyed when the phone's
 * fingerprints change. Setting it up needs the password; logging in needs a signature over a fresh
 * one-time challenge, so a copied signature or a hooked fingerprint check is of no use.
 */
@Service
public class BiometricLoginService {

    static final Duration CHALLENGE_LIFETIME = Duration.ofMinutes(2);
    static final String NOT_SET_UP = "Fingerprint login is not set up on this phone. Please log in with your password.";
    static final String FAILED = "Fingerprint login failed. Please log in with your password.";

    private final BiometricKeyRepository keyRepository;
    private final AppUserRepository userRepository;
    private final AuthService authService;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    @Autowired
    public BiometricLoginService(BiometricKeyRepository keyRepository, AppUserRepository userRepository,
                                 AuthService authService) {
        this(keyRepository, userRepository, authService, Clock.systemUTC());
    }

    BiometricLoginService(BiometricKeyRepository keyRepository, AppUserRepository userRepository,
                          AuthService authService, Clock clock) {
        this.keyRepository = keyRepository;
        this.userRepository = userRepository;
        this.authService = authService;
        this.clock = clock;
    }

    /** Sets up fingerprint login for this phone. The password proves it is the user, not just their token. */
    public String register(String username, String password, String publicKey) {
        AppUser user = authService.authenticate(username, password);
        parsePublicKey(publicKey);
        String id = UUID.randomUUID().toString();
        keyRepository.save(new BiometricKey(id, user.getUsername(), publicKey.trim(), clock.instant()));
        return id;
    }

    /** A new one-time challenge for the phone to sign; any earlier one for this key stops working. */
    public String challenge(String keyId) {
        BiometricKey key = findKey(keyId);
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        key.setChallenge(challenge, clock.instant().plus(CHALLENGE_LIFETIME));
        keyRepository.save(key);
        return challenge;
    }

    public TokenResponse login(String keyId, String signature) {
        BiometricKey key = findKey(keyId);
        String challenge = key.getChallenge();
        Instant expiresAt = key.getChallengeExpiresAt();
        // Used up before checking, so a wrong signature also costs the challenge
        if (challenge == null || keyRepository.consumeChallenge(key.getId(), challenge) != 1) {
            throw new InvalidCredentialsException(FAILED);
        }
        if (expiresAt == null || !clock.instant().isBefore(expiresAt)
                || !verify(key.getPublicKey(), signedText(key.getId(), challenge), signature)) {
            throw new InvalidCredentialsException(FAILED);
        }
        AppUser user = userRepository.findById(key.getUsername())
                .orElseThrow(() -> new InvalidCredentialsException(NOT_SET_UP));
        // LG-04 / LG-05 are checked again here, as at every login
        TokenResponse tokens = authService.issueTokens(user, AuthService.MOBILE_SESSION_LIFETIME);
        // The entity was read before the challenge was used up; don't write it back
        key.setChallenge(null, null);
        key.setLastUsedAt(clock.instant());
        keyRepository.save(key);
        return tokens;
    }

    /** Turns fingerprint login off for this phone. Knowing the key id is enough; it only removes access. */
    public void remove(String keyId) {
        if (keyId != null && !keyId.isBlank()) {
            keyRepository.deleteById(keyId);
        }
    }

    /** What the phone signs: tied to this key, so a signature can't be replayed for another one. */
    static String signedText(String keyId, String challenge) {
        return "agrum-login:" + keyId + ":" + challenge;
    }

    private BiometricKey findKey(String keyId) {
        if (keyId == null || keyId.isBlank()) {
            throw new InvalidCredentialsException(NOT_SET_UP);
        }
        return keyRepository.findById(keyId).orElseThrow(() -> new InvalidCredentialsException(NOT_SET_UP));
    }

    private static PublicKey parsePublicKey(String publicKey) {
        try {
            byte[] der = Base64.getMimeDecoder().decode(publicKey == null ? "" : publicKey.trim());
            PublicKey key = KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(der));
            if (!(key instanceof ECPublicKey ec) || ec.getParams().getCurve().getField().getFieldSize() != 256) {
                throw new IllegalArgumentException("Fingerprint key must be EC P-256.");
            }
            return key;
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalArgumentException("Fingerprint key must be EC P-256.");
        }
    }

    private static boolean verify(String publicKey, String text, String signature) {
        try {
            Signature verifier = Signature.getInstance("SHA256withECDSA");
            verifier.initVerify(parsePublicKey(publicKey));
            verifier.update(text.getBytes(StandardCharsets.UTF_8));
            return verifier.verify(Base64.getMimeDecoder().decode(signature == null ? "" : signature.trim()));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            return false;
        }
    }
}
