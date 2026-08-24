package com.atrum.agrum.auth;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    private String username;
    private String token;
    private Instant expiryDate;

    // Constructors, Getters, Setters
    public RefreshToken() {}
    public RefreshToken(String username, String token, Instant expiryDate) {
        this.username = username;
        this.token = token;
        this.expiryDate = expiryDate;
    }
    public String getUsername() { return username; }
    public String getToken() { return token; }
    public Instant getExpiryDate() { return expiryDate; }
}
