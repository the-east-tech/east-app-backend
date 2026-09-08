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
@Table(name = "stock_sku_change_requests")
public class StockSkuChangeRequest {
    @Id
    @Generated
    @ColumnDefault("uuidv7()")
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "sku_id")
    private UUID skuId;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", nullable = false, length = 16)
    private StockSkuChangeType changeType;

    @Enumerated(EnumType.STRING)
    @Column(name = "workflow_status", nullable = false, length = 16)
    private StockWorkflowStatus workflowStatus;

    @Column(name = "sku_name", nullable = false, length = 120)
    private String skuName;

    @Column(name = "payload_json", nullable = false, columnDefinition = "TEXT")
    private String payloadJson;

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

    protected StockSkuChangeRequest() {
    }

    public StockSkuChangeRequest(
            UUID tenantId,
            UUID skuId,
            StockSkuChangeType changeType,
            String skuName,
            String payloadJson,
            UUID requestedByUserId
    ) {
        this.tenantId = Objects.requireNonNull(tenantId);
        submit(skuId, changeType, skuName, payloadJson, requestedByUserId);
    }

    public void submit(
            UUID skuId,
            StockSkuChangeType changeType,
            String skuName,
            String payloadJson,
            UUID requestedByUserId
    ) {
        if (workflowStatus == StockWorkflowStatus.SUBMITTED) {
            throw new IllegalStateException("This SKU change is already awaiting approval.");
        }
        this.skuId = skuId;
        this.changeType = Objects.requireNonNull(changeType);
        this.skuName = requireText(skuName, "skuName");
        this.payloadJson = Objects.requireNonNull(payloadJson);
        this.requestedByUserId = Objects.requireNonNull(requestedByUserId);
        this.workflowStatus = StockWorkflowStatus.SUBMITTED;
        this.submittedAt = Instant.now();
        this.reviewedByUserId = null;
        this.reviewedAt = null;
        this.reviewNote = "";
    }

    public void review(StockWorkflowStatus next, String note, UUID reviewerUserId) {
        if (workflowStatus != StockWorkflowStatus.SUBMITTED) {
            throw new IllegalStateException("Only a submitted SKU change can be reviewed.");
        }
        if (next != StockWorkflowStatus.DONE && next != StockWorkflowStatus.PENDING) {
            throw new IllegalArgumentException("SKU change status must be DONE or PENDING.");
        }
        String normalised = note == null ? "" : note.trim();
        if (next == StockWorkflowStatus.PENDING && normalised.isEmpty()) {
            throw new IllegalArgumentException("A return reason is required.");
        }
        workflowStatus = next;
        reviewedByUserId = Objects.requireNonNull(reviewerUserId);
        reviewedAt = Instant.now();
        reviewNote = normalised;
    }

    public void attachSku(UUID id) {
        skuId = Objects.requireNonNull(id);
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getSkuId() { return skuId; }
    public StockSkuChangeType getChangeType() { return changeType; }
    public StockWorkflowStatus getWorkflowStatus() { return workflowStatus; }
    public String getSkuName() { return skuName; }
    public String getPayloadJson() { return payloadJson; }
    public UUID getRequestedByUserId() { return requestedByUserId; }
    public Instant getSubmittedAt() { return submittedAt; }
    public UUID getReviewedByUserId() { return reviewedByUserId; }
    public Instant getReviewedAt() { return reviewedAt; }
    public String getReviewNote() { return reviewNote; }
    public Instant getUpdatedAt() { return updatedAt; }

    private static String requireText(String value, String field) {
        String normalised = Objects.requireNonNull(value, field + " must not be null").trim();
        if (normalised.isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
        return normalised;
    }
}
