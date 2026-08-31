package com.eastapp.backend.activity;

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
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "user_notifications")
public class UserNotification {
    @Id
    @Generated
    @ColumnDefault("uuidv7()")
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "recipient_user_id", nullable = false, updatable = false)
    private UUID recipientUserId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "activity_event_id", nullable = false, updatable = false)
    private ActivityEvent activityEvent;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "dismissed_at")
    private Instant dismissedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected UserNotification() {
    }

    public UserNotification(UUID tenantId, UUID recipientUserId, ActivityEvent activityEvent) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.recipientUserId = Objects.requireNonNull(recipientUserId, "recipientUserId must not be null");
        this.activityEvent = Objects.requireNonNull(activityEvent, "activityEvent must not be null");
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getRecipientUserId() { return recipientUserId; }
    public ActivityEvent getActivityEvent() { return activityEvent; }
    public Instant getReadAt() { return readAt; }
    public Instant getDismissedAt() { return dismissedAt; }
    public Instant getCreatedAt() { return createdAt; }

    public void markRead(Instant now) {
        if (readAt == null) readAt = Objects.requireNonNull(now, "now must not be null");
    }

    public void dismiss(Instant now) {
        Instant resolved = Objects.requireNonNull(now, "now must not be null");
        markRead(resolved);
        dismissedAt = resolved;
    }
}
