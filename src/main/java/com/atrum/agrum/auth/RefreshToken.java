package com.atrum.agrum.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * One login session's refresh token. A user can have several at once (web and phone), and each is
 * rotated, revoked and expires on its own. Only a SHA-256 hash of the token is stored, so the table
 * can't be used to log in.
 */
@Entity
@Table(name = "refresh_sessions", indexes = @Index(name = "idx_refresh_sessions_username", columnList = "username"))
public class RefreshToken {

    @Id
    @Column(length = 64)
    private String tokenHash;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false)
    private Instant expiryDate;

    public RefreshToken() {}

    public RefreshToken(String tokenHash, String username, Instant expiryDate) {
        this.tokenHash = tokenHash;
        this.username = username;
        this.expiryDate = expiryDate;
    }

    public String getTokenHash() { return tokenHash; }
    public String getUsername() { return username; }
    public Instant getExpiryDate() { return expiryDate; }
}
