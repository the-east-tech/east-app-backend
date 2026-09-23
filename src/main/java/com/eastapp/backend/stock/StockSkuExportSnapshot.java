package com.eastapp.backend.stock;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "stock_sku_export_snapshots")
public class StockSkuExportSnapshot {
    @Id
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "csv_content", nullable = false, columnDefinition = "TEXT")
    private String csvContent;

    @Column(name = "approved_request_id", nullable = false)
    private UUID approvedRequestId;

    @Column(name = "approved_by_user_id", nullable = false)
    private UUID approvedByUserId;

    @Column(name = "approved_at", nullable = false)
    private Instant approvedAt;

    protected StockSkuExportSnapshot() {
    }

    public StockSkuExportSnapshot(
            UUID tenantId,
            String fileName,
            String csvContent,
            UUID approvedRequestId,
            UUID approvedByUserId
    ) {
        this.tenantId = Objects.requireNonNull(tenantId);
        overwrite(fileName, csvContent, approvedRequestId, approvedByUserId);
    }

    public void overwrite(
            String fileName,
            String csvContent,
            UUID approvedRequestId,
            UUID approvedByUserId
    ) {
        this.fileName = Objects.requireNonNull(fileName).trim();
        this.csvContent = Objects.requireNonNull(csvContent);
        this.approvedRequestId = Objects.requireNonNull(approvedRequestId);
        this.approvedByUserId = Objects.requireNonNull(approvedByUserId);
        this.approvedAt = Instant.now();
    }

    public UUID getTenantId() { return tenantId; }
    public String getFileName() { return fileName; }
    public String getCsvContent() { return csvContent; }
}
