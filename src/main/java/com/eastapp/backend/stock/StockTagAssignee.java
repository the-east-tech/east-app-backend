package com.eastapp.backend.stock;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Generated;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "stock_tag_assignees")
public class StockTagAssignee {
    @Id
    @Generated
    @ColumnDefault("uuidv7()")
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "tag_id", nullable = false, updatable = false)
    private UUID tagId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "assigned_by_user_id", nullable = false, updatable = false)
    private UUID assignedByUserId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected StockTagAssignee() {
    }

    public StockTagAssignee(UUID tenantId, UUID tagId, UUID userId, UUID assignedByUserId) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.tagId = Objects.requireNonNull(tagId, "tagId must not be null");
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.assignedByUserId = Objects.requireNonNull(assignedByUserId, "assignedByUserId must not be null");
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getTagId() { return tagId; }
    public UUID getUserId() { return userId; }
    public UUID getAssignedByUserId() { return assignedByUserId; }
    public Instant getCreatedAt() { return createdAt; }
}
