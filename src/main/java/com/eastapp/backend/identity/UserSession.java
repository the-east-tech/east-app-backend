package com.eastapp.backend.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
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
    @JoinColumn(name = "identity_id", nullable = false, updatable = false)
    private LoginIdentity identity;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "active_user_id", nullable = false)
    private UserAccount userAccount;

    @Column(name = "token_hash", nullable = false, columnDefinition = "bytea", updatable = false)
    private byte[] tokenHash;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_used_at", nullable = false)
    private Instant lastUsedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected UserSession() {
    }

    public UserSession(LoginIdentity identity, UserAccount userAccount, byte[] tokenHash) {
        this.identity = Objects.requireNonNull(identity, "identity must not be null");
        this.userAccount = requireMatchingUser(identity, userAccount);
        this.tokenHash = copyAndValidateTokenHash(tokenHash);
        this.lastUsedAt = Instant.now();
    }

    @PrePersist
    void initialiseLastUsedAt() {
        if (lastUsedAt == null) lastUsedAt = Instant.now();
    }

    public void markUsed(Instant usedAt) {
        this.lastUsedAt = Objects.requireNonNull(usedAt, "usedAt must not be null");
    }

    public void switchContext(UserAccount userAccount) {
        this.userAccount = requireMatchingUser(identity, userAccount);
        markUsed(Instant.now());
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

    public LoginIdentity getIdentity() {
        return identity;
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

    private static UserAccount requireMatchingUser(
            LoginIdentity identity,
            UserAccount userAccount
    ) {
        UserAccount resolved = Objects.requireNonNull(userAccount, "userAccount must not be null");
        if (!resolved.getIdentity().getId().equals(identity.getId())) {
            throw new IllegalArgumentException("userAccount must belong to the session identity");
        }
        return resolved;
    }

    private static byte[] copyAndValidateTokenHash(byte[] tokenHash) {
        Objects.requireNonNull(tokenHash, "tokenHash must not be null");
        if (tokenHash.length != SHA_256_BYTES) {
            throw new IllegalArgumentException("tokenHash must contain exactly 32 bytes");
        }
        return Arrays.copyOf(tokenHash, tokenHash.length);
    }
}
