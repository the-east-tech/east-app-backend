package com.eastapp.backend.reports;

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
@Table(name = "daily_report_photos")
public class DailyReportPhoto {
    @Id
    @Generated
    @ColumnDefault("uuidv7()")
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "report_id", nullable = false, updatable = false)
    private UUID reportId;

    @Column(name = "photo_media_id", nullable = false, updatable = false)
    private UUID photoMediaId;

    @Column(name = "created_by_user_id", nullable = false, updatable = false)
    private UUID createdByUserId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected DailyReportPhoto() {
    }

    public DailyReportPhoto(UUID tenantId, UUID reportId, UUID photoMediaId, UUID createdByUserId) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.reportId = Objects.requireNonNull(reportId, "reportId must not be null");
        this.photoMediaId = Objects.requireNonNull(photoMediaId, "photoMediaId must not be null");
        this.createdByUserId = Objects.requireNonNull(createdByUserId, "createdByUserId must not be null");
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getReportId() { return reportId; }
    public UUID getPhotoMediaId() { return photoMediaId; }
    public UUID getCreatedByUserId() { return createdByUserId; }
    public Instant getCreatedAt() { return createdAt; }
}
