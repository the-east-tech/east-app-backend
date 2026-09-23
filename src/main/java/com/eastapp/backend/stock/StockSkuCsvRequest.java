package com.eastapp.backend.stock;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "stock_sku_csv_requests")
public class StockSkuCsvRequest {
    @Id
    @Generated
    @ColumnDefault("uuidv7()")
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private StockSkuCsvOperation operation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private StockSkuCsvRequestStatus status;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "csv_content", nullable = false, columnDefinition = "TEXT")
    private String csvContent;

    @Column(name = "total_rows", nullable = false)
    private int totalRows;

    @Column(name = "ready_rows", nullable = false)
    private int readyRows;

    @Column(name = "duplicate_rows", nullable = false)
    private int duplicateRows;

    @Column(name = "new_tag_count", nullable = false)
    private int newTagCount;

    @Column(name = "unmatched_supplier_count", nullable = false)
    private int unmatchedSupplierCount;

    @Column(name = "requested_by_user_id", nullable = false)
    private UUID requestedByUserId;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @Column(name = "reviewed_by_user_id")
    private UUID reviewedByUserId;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "review_note", nullable = false, length = 1000)
    private String reviewNote = "";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected StockSkuCsvRequest() {
    }

    public StockSkuCsvRequest(
            UUID tenantId,
            StockSkuCsvOperation operation,
            String fileName,
            String csvContent,
            int totalRows,
            int readyRows,
            int duplicateRows,
            int newTagCount,
            int unmatchedSupplierCount,
            UUID requestedByUserId
    ) {
        this.tenantId = Objects.requireNonNull(tenantId);
        this.operation = Objects.requireNonNull(operation);
        this.fileName = requireText(fileName, "fileName");
        this.csvContent = requireContent(csvContent);
        this.totalRows = nonNegative(totalRows, "totalRows");
        this.readyRows = nonNegative(readyRows, "readyRows");
        this.duplicateRows = nonNegative(duplicateRows, "duplicateRows");
        this.newTagCount = nonNegative(newTagCount, "newTagCount");
        this.unmatchedSupplierCount = nonNegative(
                unmatchedSupplierCount,
                "unmatchedSupplierCount"
        );
        this.requestedByUserId = Objects.requireNonNull(requestedByUserId);
        this.status = StockSkuCsvRequestStatus.SUBMITTED;
        this.submittedAt = Instant.now();
    }

    public void approve(UUID reviewerUserId, String note) {
        review(StockSkuCsvRequestStatus.APPROVED, reviewerUserId, note);
    }

    public void reject(UUID reviewerUserId, String note) {
        String reason = note == null ? "" : note.trim();
        if (reason.isEmpty()) {
            throw new IllegalArgumentException("A rejection reason is required.");
        }
        review(StockSkuCsvRequestStatus.REJECTED, reviewerUserId, reason);
    }

    private void review(
            StockSkuCsvRequestStatus next,
            UUID reviewerUserId,
            String note
    ) {
        if (status != StockSkuCsvRequestStatus.SUBMITTED) {
            throw new IllegalStateException("Only a submitted CSV request can be reviewed.");
        }
        status = Objects.requireNonNull(next);
        reviewedByUserId = Objects.requireNonNull(reviewerUserId);
        reviewedAt = Instant.now();
        reviewNote = note == null ? "" : note.trim();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public StockSkuCsvOperation getOperation() { return operation; }
    public StockSkuCsvRequestStatus getStatus() { return status; }
    public String getFileName() { return fileName; }
    public String getCsvContent() { return csvContent; }
    public int getTotalRows() { return totalRows; }
    public int getReadyRows() { return readyRows; }
    public int getDuplicateRows() { return duplicateRows; }
    public int getNewTagCount() { return newTagCount; }
    public int getUnmatchedSupplierCount() { return unmatchedSupplierCount; }
    public UUID getRequestedByUserId() { return requestedByUserId; }
    public Instant getSubmittedAt() { return submittedAt; }
    public UUID getReviewedByUserId() { return reviewedByUserId; }
    public Instant getReviewedAt() { return reviewedAt; }
    public String getReviewNote() { return reviewNote; }
    public Instant getUpdatedAt() { return updatedAt; }

    private static String requireText(String value, String field) {
        String normalised = Objects.requireNonNull(value, field + " must not be null").trim();
        if (normalised.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalised;
    }

    private static String requireContent(String value) {
        String content = Objects.requireNonNull(value, "csvContent must not be null");
        if (content.isBlank()) {
            throw new IllegalArgumentException("csvContent must not be blank");
        }
        return content;
    }

    private static int nonNegative(int value, String field) {
        if (value < 0) throw new IllegalArgumentException(field + " must not be negative");
        return value;
    }
}
