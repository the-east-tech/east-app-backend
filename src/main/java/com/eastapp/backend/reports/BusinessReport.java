package com.eastapp.backend.reports;

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
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "business_reports")
public class BusinessReport {
    @Id
    @Generated
    @ColumnDefault("uuidv7()")
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "report_type", nullable = false, updatable = false, length = 24)
    private BusinessReportType reportType;

    @Column(name = "report_date", nullable = false, updatable = false)
    private LocalDate reportDate;

    @Column(name = "submitted_by_user_id", nullable = false)
    private UUID submittedByUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "workflow_status", nullable = false, length = 16)
    private ReportWorkflowStatus workflowStatus;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "reviewed_by_user_id")
    private UUID reviewedByUserId;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "review_note", length = 500)
    private String reviewNote;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected BusinessReport() {
    }

    public BusinessReport(
            UUID tenantId,
            BusinessReportType reportType,
            LocalDate reportDate,
            UUID submittedByUserId
    ) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.reportType = Objects.requireNonNull(reportType, "reportType must not be null");
        this.reportDate = Objects.requireNonNull(reportDate, "reportDate must not be null");
        this.submittedByUserId = Objects.requireNonNull(submittedByUserId, "submittedByUserId must not be null");
        this.workflowStatus = ReportWorkflowStatus.DRAFT;
    }


    public void assignSubmitter(UUID userId) {
        if (workflowStatus != ReportWorkflowStatus.DRAFT
                && workflowStatus != ReportWorkflowStatus.REJECTED) {
            throw new IllegalStateException("Submitted or approved reports are locked");
        }
        submittedByUserId = Objects.requireNonNull(userId, "userId must not be null");
    }

    public void submit() {
        if (workflowStatus != ReportWorkflowStatus.DRAFT
                && workflowStatus != ReportWorkflowStatus.REJECTED) {
            throw new IllegalStateException("Only a draft or rejected report can be submitted");
        }
        workflowStatus = ReportWorkflowStatus.SUBMITTED;
        submittedAt = Instant.now();
        reviewedByUserId = null;
        reviewedAt = null;
        reviewNote = null;
    }

    public void markCompleteWithoutApproval() {
        workflowStatus = ReportWorkflowStatus.APPROVED;
        submittedAt = Instant.now();
        reviewedByUserId = submittedByUserId;
        reviewedAt = submittedAt;
        reviewNote = "No approval required";
    }

    public void approve(UUID reviewerUserId, String note) {
        requireSubmitted();
        workflowStatus = ReportWorkflowStatus.APPROVED;
        reviewedByUserId = Objects.requireNonNull(reviewerUserId, "reviewerUserId must not be null");
        reviewedAt = Instant.now();
        reviewNote = normaliseOptional(note);
    }

    public void reject(UUID reviewerUserId, String note) {
        requireSubmitted();
        String normalised = requireText(note, "A rejection reason is required.");
        workflowStatus = ReportWorkflowStatus.REJECTED;
        reviewedByUserId = Objects.requireNonNull(reviewerUserId, "reviewerUserId must not be null");
        reviewedAt = Instant.now();
        reviewNote = normalised;
    }

    public void reopenForEditing() {
        if (workflowStatus == ReportWorkflowStatus.REJECTED) {
            workflowStatus = ReportWorkflowStatus.DRAFT;
            submittedAt = null;
            reviewedByUserId = null;
            reviewedAt = null;
            reviewNote = null;
        }
        if (workflowStatus != ReportWorkflowStatus.DRAFT) {
            throw new IllegalStateException("Approved or submitted reports are locked");
        }
    }

    private void requireSubmitted() {
        if (workflowStatus != ReportWorkflowStatus.SUBMITTED) {
            throw new IllegalStateException("Only submitted reports can be reviewed");
        }
    }

    private static String normaliseOptional(String value) {
        if (value == null) return null;
        String normalised = value.trim();
        return normalised.isEmpty() ? null : normalised;
    }

    private static String requireText(String value, String message) {
        String normalised = value == null ? "" : value.trim();
        if (normalised.isEmpty()) throw new IllegalArgumentException(message);
        return normalised;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public BusinessReportType getReportType() { return reportType; }
    public LocalDate getReportDate() { return reportDate; }
    public UUID getSubmittedByUserId() { return submittedByUserId; }
    public ReportWorkflowStatus getWorkflowStatus() { return workflowStatus; }
    public Instant getSubmittedAt() { return submittedAt; }
    public UUID getReviewedByUserId() { return reviewedByUserId; }
    public Instant getReviewedAt() { return reviewedAt; }
    public String getReviewNote() { return reviewNote; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
