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
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "push_outbox")
public class PushOutbox {
    @Id
    @Generated
    @ColumnDefault("uuidv7()")
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "notification_id", nullable = false, updatable = false)
    private UserNotification notification;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_id", nullable = false, updatable = false)
    private PushDevice device;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PushOutbox() {
    }

    public PushOutbox(UserNotification notification, PushDevice device, Instant now) {
        this.notification = Objects.requireNonNull(notification, "notification must not be null");
        this.device = Objects.requireNonNull(device, "device must not be null");
        this.nextAttemptAt = Objects.requireNonNull(now, "now must not be null");
        this.expiresAt = now.plus(24, ChronoUnit.HOURS);
    }

    public UUID getId() { return id; }
    public UserNotification getNotification() { return notification; }
    public PushDevice getDevice() { return device; }
    public int getAttempts() { return attempts; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getSentAt() { return sentAt; }

    public void sent(Instant now) {
        sentAt = Objects.requireNonNull(now, "now must not be null");
        lastError = null;
    }

    public void failed(Instant now, String error) {
        attempts += 1;
        long delaySeconds = Math.min(3600L, 15L * (1L << Math.min(attempts - 1, 8)));
        nextAttemptAt = Objects.requireNonNull(now, "now must not be null")
                .plus(delaySeconds, ChronoUnit.SECONDS);
        String value = error == null ? "Push delivery failed" : error.trim();
        lastError = value.substring(0, Math.min(value.length(), 500));
    }
}
