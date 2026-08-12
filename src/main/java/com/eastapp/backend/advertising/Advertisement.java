package com.eastapp.backend.advertising;

import com.eastapp.backend.organisation.Tenant;
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
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "advertisements")
public class Advertisement {
    @Id
    @Generated
    @ColumnDefault("uuidv7()")
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false, updatable = false)
    private Tenant tenant;

    @Column(name = "image_storage_key", nullable = false, length = 80)
    private String imageStorageKey;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_by_user_id", nullable = false, updatable = false)
    private UUID createdByUserId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Advertisement() {
    }

    public Advertisement(
            Tenant tenant,
            String imageStorageKey,
            Instant startsAt,
            Instant endsAt,
            int displayOrder,
            boolean active,
            UUID createdByUserId
    ) {
        this.tenant = Objects.requireNonNull(tenant, "tenant must not be null");
        this.imageStorageKey = Objects.requireNonNull(imageStorageKey, "imageStorageKey must not be null");
        this.startsAt = Objects.requireNonNull(startsAt, "startsAt must not be null");
        this.endsAt = Objects.requireNonNull(endsAt, "endsAt must not be null");
        this.displayOrder = displayOrder;
        this.active = active;
        this.createdByUserId = Objects.requireNonNull(createdByUserId, "createdByUserId must not be null");
    }

    public UUID getId() { return id; }
    public Tenant getTenant() { return tenant; }
    public String getImageStorageKey() { return imageStorageKey; }
    public Instant getStartsAt() { return startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public int getDisplayOrder() { return displayOrder; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void update(
            String imageStorageKey,
            Instant startsAt,
            Instant endsAt,
            int displayOrder,
            boolean active
    ) {
        this.imageStorageKey = Objects.requireNonNull(imageStorageKey, "imageStorageKey must not be null");
        this.startsAt = Objects.requireNonNull(startsAt, "startsAt must not be null");
        this.endsAt = Objects.requireNonNull(endsAt, "endsAt must not be null");
        this.displayOrder = displayOrder;
        this.active = active;
    }
}
