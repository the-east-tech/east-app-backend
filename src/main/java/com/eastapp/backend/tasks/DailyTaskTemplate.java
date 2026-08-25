package com.eastapp.backend.tasks;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "daily_task_templates")
public class DailyTaskTemplate {
    @Id
    @Generated
    @ColumnDefault("uuidv7()")
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "tag_id", nullable = false)
    private UUID tagId;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(nullable = false, length = 1000)
    private String instruction;

    @Column(name = "required_photo_count", nullable = false)
    private int requiredPhotoCount;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_by_user_id", nullable = false, updatable = false)
    private UUID createdByUserId;

    @Column(name = "updated_by_user_id", nullable = false)
    private UUID updatedByUserId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DailyTaskTemplate() {
    }

    public DailyTaskTemplate(
            UUID tenantId,
            UUID tagId,
            String title,
            String instruction,
            int requiredPhotoCount,
            boolean active,
            UUID actorUserId
    ) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.createdByUserId = Objects.requireNonNull(actorUserId, "actorUserId must not be null");
        update(tagId, title, instruction, requiredPhotoCount, active, actorUserId);
    }

    public void update(
            UUID tagId,
            String title,
            String instruction,
            int requiredPhotoCount,
            boolean active,
            UUID actorUserId
    ) {
        this.tagId = Objects.requireNonNull(tagId, "tagId must not be null");
        this.title = requireText(title, "title");
        this.instruction = optionalText(instruction);
        if (requiredPhotoCount < 1 || requiredPhotoCount > 40) {
            throw new IllegalArgumentException("requiredPhotoCount must be between 1 and 40");
        }
        this.requiredPhotoCount = requiredPhotoCount;
        this.active = active;
        this.updatedByUserId = Objects.requireNonNull(actorUserId, "actorUserId must not be null");
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getTagId() { return tagId; }
    public String getTitle() { return title; }
    public String getInstruction() { return instruction; }
    public int getRequiredPhotoCount() { return requiredPhotoCount; }
    public boolean isActive() { return active; }
    public UUID getCreatedByUserId() { return createdByUserId; }
    public UUID getUpdatedByUserId() { return updatedByUserId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    private static String requireText(String value, String field) {
        String normalised = Objects.requireNonNull(value, field + " must not be null").trim();
        if (normalised.isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
        return normalised;
    }

    private static String optionalText(String value) {
        return value == null ? "" : value.trim();
    }
}
