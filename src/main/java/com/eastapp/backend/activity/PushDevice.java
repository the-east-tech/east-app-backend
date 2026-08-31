package com.eastapp.backend.activity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "push_devices")
public class PushDevice {
    @Id
    @Generated
    @ColumnDefault("uuidv7()")
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(nullable = false, length = 2048)
    private String token;

    @Column(nullable = false, length = 16)
    private String platform;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PushDevice() {
    }

    public PushDevice(
            UUID tenantId,
            UUID userId,
            UUID sessionId,
            String token,
            String platform,
            Instant now
    ) {
        reassign(tenantId, userId, sessionId, platform, now);
        this.token = requireText(token, "token");
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getUserId() { return userId; }
    public UUID getSessionId() { return sessionId; }
    public String getToken() { return token; }
    public String getPlatform() { return platform; }
    public boolean isActive() { return active; }
    public Instant getLastSeenAt() { return lastSeenAt; }

    public void reassign(
            UUID tenantId,
            UUID userId,
            UUID sessionId,
            String platform,
            Instant now
    ) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId must not be null");
        this.platform = requireText(platform, "platform").toUpperCase(Locale.ROOT);
        this.lastSeenAt = Objects.requireNonNull(now, "now must not be null");
        this.active = true;
    }

    public void deactivate() {
        active = false;
    }

    private static String requireText(String value, String fieldName) {
        String normalised = Objects.requireNonNull(value, fieldName + " must not be null").trim();
        if (normalised.isEmpty()) throw new IllegalArgumentException(fieldName + " must not be blank");
        return normalised;
    }
}
