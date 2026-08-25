package com.eastapp.backend.tasks;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Generated;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "daily_task_record_checklist_items")
public class DailyTaskRecordChecklistItem {
    @Id
    @Generated
    @ColumnDefault("uuidv7()")
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "record_id", nullable = false, updatable = false)
    private UUID recordId;

    @Column(nullable = false, updatable = false)
    private int position;

    @Column(nullable = false, length = 300, updatable = false)
    private String description;

    @Column(name = "completed_by_user_id")
    private UUID completedByUserId;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected DailyTaskRecordChecklistItem() {
    }

    public DailyTaskRecordChecklistItem(
            UUID tenantId,
            UUID recordId,
            int position,
            String description
    ) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.recordId = Objects.requireNonNull(recordId, "recordId must not be null");
        this.position = position;
        this.description = Objects.requireNonNull(description, "description must not be null").trim();
    }

    public void setCompleted(boolean completed, UUID actorUserId, Instant when) {
        if (completed) {
            completedByUserId = Objects.requireNonNull(actorUserId, "actorUserId must not be null");
            completedAt = Objects.requireNonNull(when, "when must not be null");
        } else {
            completedByUserId = null;
            completedAt = null;
        }
    }

    public boolean isCompleted() { return completedAt != null; }
    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getRecordId() { return recordId; }
    public int getPosition() { return position; }
    public String getDescription() { return description; }
    public UUID getCompletedByUserId() { return completedByUserId; }
    public Instant getCompletedAt() { return completedAt; }
}
