package com.eastapp.backend.reports;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Generated;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "sales_void_bills")
public class SalesVoidBill {
    @Id
    @Generated
    @ColumnDefault("uuidv7()")
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "sales_report_id", nullable = false, updatable = false)
    private UUID salesReportId;

    @Column(name = "photo_media_id", nullable = false, updatable = false)
    private UUID photoMediaId;

    @Column(name = "bill_number", nullable = false, updatable = false, length = 80)
    private String billNumber;

    @Column(nullable = false, updatable = false, length = 500)
    private String reason;

    @Column(name = "amount_rm", nullable = false, updatable = false, precision = 14, scale = 2)
    private BigDecimal amountRm;

    @Column(name = "created_by_user_id", nullable = false, updatable = false)
    private UUID createdByUserId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected SalesVoidBill() {
    }

    public SalesVoidBill(
            UUID tenantId,
            UUID salesReportId,
            UUID photoMediaId,
            String billNumber,
            String reason,
            BigDecimal amountRm,
            UUID createdByUserId
    ) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.salesReportId = Objects.requireNonNull(salesReportId, "salesReportId must not be null");
        this.photoMediaId = Objects.requireNonNull(photoMediaId, "photoMediaId must not be null");
        this.billNumber = requireText(billNumber, "billNumber");
        this.reason = requireText(reason, "reason");
        this.amountRm = Objects.requireNonNull(amountRm, "amountRm must not be null");
        if (amountRm.signum() <= 0) throw new IllegalArgumentException("amountRm must be positive");
        this.createdByUserId = Objects.requireNonNull(createdByUserId, "createdByUserId must not be null");
    }

    private static String requireText(String value, String name) {
        String normalised = Objects.requireNonNull(value, name + " must not be null").trim();
        if (normalised.isEmpty()) throw new IllegalArgumentException(name + " must not be blank");
        return normalised;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getSalesReportId() { return salesReportId; }
    public UUID getPhotoMediaId() { return photoMediaId; }
    public String getBillNumber() { return billNumber; }
    public String getReason() { return reason; }
    public BigDecimal getAmountRm() { return amountRm; }
    public UUID getCreatedByUserId() { return createdByUserId; }
    public Instant getCreatedAt() { return createdAt; }
}
