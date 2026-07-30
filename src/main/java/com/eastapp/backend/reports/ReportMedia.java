package com.eastapp.backend.reports;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Generated;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "report_media")
public class ReportMedia {
    @Id
    @Generated
    @ColumnDefault("uuidv7()")
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "storage_key", nullable = false, updatable = false, length = 80)
    private String storageKey;

    @Column(name = "content_type", nullable = false, updatable = false, length = 40)
    private String contentType;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private long sizeBytes;

    @Column(name = "content_bytes", nullable = false, updatable = false, columnDefinition = "bytea")
    private byte[] contentBytes;

    @Column(name = "uploaded_by_user_id", nullable = false, updatable = false)
    private UUID uploadedByUserId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ReportMedia() {
    }

    public ReportMedia(
            UUID tenantId,
            String storageKey,
            String contentType,
            byte[] contentBytes,
            UUID uploadedByUserId
    ) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.storageKey = Objects.requireNonNull(storageKey, "storageKey must not be null");
        this.contentType = Objects.requireNonNull(contentType, "contentType must not be null");
        this.contentBytes = Arrays.copyOf(contentBytes, contentBytes.length);
        this.sizeBytes = contentBytes.length;
        this.uploadedByUserId = Objects.requireNonNull(uploadedByUserId, "uploadedByUserId must not be null");
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getStorageKey() { return storageKey; }
    public String getContentType() { return contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public byte[] getContentBytes() { return Arrays.copyOf(contentBytes, contentBytes.length); }
    public UUID getUploadedByUserId() { return uploadedByUserId; }
    public Instant getCreatedAt() { return createdAt; }
}
