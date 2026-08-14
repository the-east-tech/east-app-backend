package com.eastapp.backend.attendance;

import com.eastapp.backend.organisation.Tenant;
import com.eastapp.backend.people.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "attendance_qr_codes")
public class AttendanceQrCode {

    @Id
    @Generated
    @ColumnDefault("uuidv7()")
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false, updatable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "generated_by_user_id", nullable = false, updatable = false)
    private UserAccount generatedByUser;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 16, updatable = false)
    private AttendanceEventType eventType;

    @Column(name = "secret_hash", nullable = false, updatable = false, columnDefinition = "bytea")
    private byte[] secretHash;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AttendanceQrCode() {
    }

    public AttendanceQrCode(
            Tenant tenant,
            UserAccount generatedByUser,
            AttendanceEventType eventType,
            byte[] secretHash,
            Instant expiresAt
    ) {
        this.tenant = Objects.requireNonNull(tenant, "tenant must not be null");
        this.generatedByUser = Objects.requireNonNull(generatedByUser, "generatedByUser must not be null");
        this.eventType = Objects.requireNonNull(eventType, "eventType must not be null");
        this.secretHash = Arrays.copyOf(Objects.requireNonNull(secretHash, "secretHash must not be null"), secretHash.length);
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    }

    public UUID getId() { return id; }
    public Tenant getTenant() { return tenant; }
    public UserAccount getGeneratedByUser() { return generatedByUser; }
    public AttendanceEventType getEventType() { return eventType; }
    public byte[] getSecretHash() { return Arrays.copyOf(secretHash, secretHash.length); }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public Instant getCreatedAt() { return createdAt; }

    public void revoke(Instant revokedAt) {
        if (this.revokedAt == null) {
            this.revokedAt = Objects.requireNonNull(revokedAt, "revokedAt must not be null");
        }
    }
}

