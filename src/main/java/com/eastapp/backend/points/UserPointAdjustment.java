package com.eastapp.backend.points;

import com.eastapp.backend.organisation.Tenant;
import com.eastapp.backend.people.UserAccount;
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

/**
 * Immutable tenant-scoped ledger entry for one user point adjustment.
 */
@Entity
@Table(name = "user_point_adjustments")
public class UserPointAdjustment {

    @Id
    @Generated
    @ColumnDefault("uuidv7()")
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false, updatable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_user_id", nullable = false, updatable = false)
    private UserAccount recipient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "adjusted_by_user_id", nullable = false, updatable = false)
    private UserAccount adjustedBy;

    @Column(name = "points_delta", nullable = false, updatable = false)
    private int pointsDelta;

    @Column(nullable = false, updatable = false, length = 300)
    private String reason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected UserPointAdjustment() {
    }

    public UserPointAdjustment(
            Tenant tenant,
            UserAccount recipient,
            UserAccount adjustedBy,
            int pointsDelta,
            String reason
    ) {
        if (pointsDelta == 0 || pointsDelta < -10 || pointsDelta > 10) {
            throw new IllegalArgumentException("pointsDelta must be between -10 and 10 and must not be zero");
        }
        String normalisedReason = Objects.requireNonNull(reason, "reason must not be null").trim();
        if (normalisedReason.isEmpty()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
        this.tenant = Objects.requireNonNull(tenant, "tenant must not be null");
        this.recipient = Objects.requireNonNull(recipient, "recipient must not be null");
        this.adjustedBy = Objects.requireNonNull(adjustedBy, "adjustedBy must not be null");
        this.pointsDelta = pointsDelta;
        this.reason = normalisedReason;
    }

    public UUID getId() {
        return id;
    }

    public Tenant getTenant() {
        return tenant;
    }

    public UserAccount getRecipient() {
        return recipient;
    }

    public UserAccount getAdjustedBy() {
        return adjustedBy;
    }

    public int getPointsDelta() {
        return pointsDelta;
    }

    public String getReason() {
        return reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
