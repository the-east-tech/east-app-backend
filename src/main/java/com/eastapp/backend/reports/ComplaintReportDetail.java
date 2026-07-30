package com.eastapp.backend.reports;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "complaint_report_details")
public class ComplaintReportDetail {
    @Id
    @Column(name = "report_id", nullable = false, updatable = false)
    private UUID reportId;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "photo_media_id", nullable = false, updatable = false)
    private UUID photoMediaId;

    @Enumerated(EnumType.STRING)
    @Column(name = "customer_gender", nullable = false, updatable = false, length = 16)
    private CustomerGender customerGender;

    @Column(name = "estimated_age", nullable = false, updatable = false)
    private int estimatedAge;

    @Column(name = "complaint_info", nullable = false, updatable = false, length = 1500)
    private String complaintInfo;

    @Column(name = "phone_e164", updatable = false, length = 32)
    private String phoneE164;

    @Column(name = "action_taken", nullable = false, length = 1500)
    private String actionTaken;

    @Column(name = "compensation_amount_rm", precision = 14, scale = 2)
    private BigDecimal compensationAmountRm;

    @Enumerated(EnumType.STRING)
    @Column(name = "complaint_status", nullable = false, length = 16)
    private ComplaintStatus complaintStatus;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    protected ComplaintReportDetail() {
    }

    public ComplaintReportDetail(
            UUID reportId,
            UUID tenantId,
            UUID photoMediaId,
            CustomerGender customerGender,
            int estimatedAge,
            String complaintInfo,
            String phoneE164,
            String actionTaken,
            BigDecimal compensationAmountRm,
            ComplaintStatus complaintStatus
    ) {
        this.reportId = Objects.requireNonNull(reportId, "reportId must not be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.photoMediaId = Objects.requireNonNull(photoMediaId, "photoMediaId must not be null");
        this.customerGender = Objects.requireNonNull(customerGender, "customerGender must not be null");
        if (estimatedAge < 1 || estimatedAge > 120) {
            throw new IllegalArgumentException("estimatedAge must be between 1 and 120");
        }
        this.estimatedAge = estimatedAge;
        this.complaintInfo = requireText(complaintInfo, "complaintInfo");
        this.phoneE164 = normaliseOptional(phoneE164);
        updateResolution(actionTaken, compensationAmountRm, complaintStatus);
    }

    public void updateResolution(
            String actionTaken,
            BigDecimal compensationAmountRm,
            ComplaintStatus complaintStatus
    ) {
        this.actionTaken = requireText(actionTaken, "actionTaken");
        if (compensationAmountRm != null && compensationAmountRm.signum() < 0) {
            throw new IllegalArgumentException("compensationAmountRm must not be negative");
        }
        this.compensationAmountRm = compensationAmountRm;
        this.complaintStatus = Objects.requireNonNull(complaintStatus, "complaintStatus must not be null");
        this.resolvedAt = complaintStatus == ComplaintStatus.RESOLVED ? Instant.now() : null;
    }

    private static String requireText(String value, String name) {
        String normalised = Objects.requireNonNull(value, name + " must not be null").trim();
        if (normalised.isEmpty()) throw new IllegalArgumentException(name + " must not be blank");
        return normalised;
    }

    private static String normaliseOptional(String value) {
        if (value == null) return null;
        String normalised = value.trim();
        return normalised.isEmpty() ? null : normalised;
    }

    public UUID getReportId() { return reportId; }
    public UUID getTenantId() { return tenantId; }
    public UUID getPhotoMediaId() { return photoMediaId; }
    public CustomerGender getCustomerGender() { return customerGender; }
    public int getEstimatedAge() { return estimatedAge; }
    public String getComplaintInfo() { return complaintInfo; }
    public String getPhoneE164() { return phoneE164; }
    public String getActionTaken() { return actionTaken; }
    public BigDecimal getCompensationAmountRm() { return compensationAmountRm; }
    public ComplaintStatus getComplaintStatus() { return complaintStatus; }
    public Instant getResolvedAt() { return resolvedAt; }
}
