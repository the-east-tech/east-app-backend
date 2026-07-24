package com.eastapp.backend.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Generated;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "user_sessions")
public class UserSession {

    private static final int SHA_256_BYTES = 32;

    @Id
    @Generated
    @ColumnDefault("uuidv7()")
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private UserAccount userAccount;

    @Column(name = "token_hash", nullable = false, columnDefinition = "bytea", updatable = false)
    private byte[] tokenHash;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @CreationTimestamp
    @Column(name = "last_used_at", nullable = false)
    private Instant lastUsedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected UserSession() {
    }

    public UserSession(UserAccount userAccount, byte[] tokenHash) {
        this.userAccount = Objects.requireNonNull(userAccount, "userAccount must not be null");
        this.tokenHash = copyAndValidateTokenHash(tokenHash);
    }

    public void markUsed(Instant usedAt) {
        this.lastUsedAt = Objects.requireNonNull(usedAt, "usedAt must not be null");
    }

    public void revoke(Instant revokedAt) {
        this.revokedAt = Objects.requireNonNull(revokedAt, "revokedAt must not be null");
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public UUID getId() {
        return id;
    }

    public UserAccount getUserAccount() {
        return userAccount;
    }

    public byte[] getTokenHash() {
        return Arrays.copyOf(tokenHash, tokenHash.length);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    private static byte[] copyAndValidateTokenHash(byte[] tokenHash) {
        Objects.requireNonNull(tokenHash, "tokenHash must not be null");
        if (tokenHash.length != SHA_256_BYTES) {
            throw new IllegalArgumentException("tokenHash must contain exactly 32 bytes");
        }
        return Arrays.copyOf(tokenHash, tokenHash.length);
    }
}
