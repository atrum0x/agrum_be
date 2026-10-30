package com.atrum.agrum.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * A phone's fingerprint login key. The private key stays in the phone's Keystore and signs only after
 * the fingerprint is checked; the server keeps the public key and verifies a signature over a one-time
 * challenge. Removed with the user's sessions (password change, made Inactive).
 */
@Entity
@Table(name = "biometric_keys", indexes = @Index(name = "idx_biometric_keys_username", columnList = "username"))
public class BiometricKey {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false)
    private String username;

    // X.509 SubjectPublicKeyInfo, base64. EC P-256
    @Column(nullable = false, columnDefinition = "TEXT")
    private String publicKey;

    // The last challenge handed out; cleared when a login uses it, so it works once
    private String challenge;

    private Instant challengeExpiresAt;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant lastUsedAt;

    public BiometricKey() {}

    public BiometricKey(String id, String username, String publicKey, Instant createdAt) {
        this.id = id;
        this.username = username;
        this.publicKey = publicKey;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public String getUsername() { return username; }
    public String getPublicKey() { return publicKey; }
    public String getChallenge() { return challenge; }
    public Instant getChallengeExpiresAt() { return challengeExpiresAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastUsedAt() { return lastUsedAt; }

    public void setChallenge(String challenge, Instant expiresAt) {
        this.challenge = challenge;
        this.challengeExpiresAt = expiresAt;
    }

    public void setLastUsedAt(Instant lastUsedAt) { this.lastUsedAt = lastUsedAt; }
}
