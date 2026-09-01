package com.eastapp.backend.tasks;

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
@Table(name = "task_photos")
public class TaskPhoto {
    @Id
    @Generated
    @ColumnDefault("uuidv7()")
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "record_id", nullable = false, updatable = false)
    private UUID recordId;

    @Column(name = "photo_media_id", nullable = false, updatable = false)
    private UUID photoMediaId;

    @Column(name = "submitted_by_user_id", nullable = false, updatable = false)
    private UUID submittedByUserId;

    @CreationTimestamp
    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt;

    protected TaskPhoto() {
    }

    public TaskPhoto(UUID tenantId, UUID recordId, UUID photoMediaId, UUID submittedByUserId) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.recordId = Objects.requireNonNull(recordId, "recordId must not be null");
        this.photoMediaId = Objects.requireNonNull(photoMediaId, "photoMediaId must not be null");
        this.submittedByUserId = Objects.requireNonNull(submittedByUserId, "submittedByUserId must not be null");
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getRecordId() { return recordId; }
    public UUID getPhotoMediaId() { return photoMediaId; }
    public UUID getSubmittedByUserId() { return submittedByUserId; }
    public Instant getSubmittedAt() { return submittedAt; }
}
