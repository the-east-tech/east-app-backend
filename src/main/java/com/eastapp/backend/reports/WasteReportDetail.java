package com.eastapp.backend.reports;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "waste_report_details")
public class WasteReportDetail {
    @Id
    @Column(name = "report_id", nullable = false, updatable = false)
    private UUID reportId;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "sku_id", updatable = false)
    private UUID skuId;

    @Column(name = "item_name", nullable = false, updatable = false, length = 160)
    private String itemName;

    @Column(nullable = false, updatable = false, precision = 14, scale = 2)
    private BigDecimal quantity;

    @Column(nullable = false, updatable = false, length = 32)
    private String unit;

    @Column(name = "estimated_unit_cost_rm", nullable = false, updatable = false, precision = 14, scale = 2)
    private BigDecimal estimatedUnitCostRm;

    @Column(nullable = false, updatable = false, length = 500)
    private String reason;

    @Column(name = "photo_media_id", nullable = false, updatable = false)
    private UUID photoMediaId;

    protected WasteReportDetail() {
    }

    public WasteReportDetail(
            UUID reportId,
            UUID tenantId,
            UUID skuId,
            String itemName,
            BigDecimal quantity,
            String unit,
            BigDecimal estimatedUnitCostRm,
            String reason,
            UUID photoMediaId
    ) {
        this.reportId = Objects.requireNonNull(reportId, "reportId must not be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.skuId = skuId;
        this.itemName = requireText(itemName, "itemName");
        this.quantity = positive(quantity, "quantity");
        this.unit = requireText(unit, "unit");
        this.estimatedUnitCostRm = nonNegative(estimatedUnitCostRm, "estimatedUnitCostRm");
        this.reason = requireText(reason, "reason");
        this.photoMediaId = Objects.requireNonNull(photoMediaId, "photoMediaId must not be null");
    }

    public BigDecimal estimatedLossRm() {
        return quantity.multiply(estimatedUnitCostRm);
    }

    private static BigDecimal positive(BigDecimal value, String name) {
        BigDecimal required = Objects.requireNonNull(value, name + " must not be null");
        if (required.signum() <= 0) throw new IllegalArgumentException(name + " must be positive");
        return required;
    }

    private static BigDecimal nonNegative(BigDecimal value, String name) {
        BigDecimal required = Objects.requireNonNull(value, name + " must not be null");
        if (required.signum() < 0) throw new IllegalArgumentException(name + " must not be negative");
        return required;
    }

    private static String requireText(String value, String name) {
        String normalised = Objects.requireNonNull(value, name + " must not be null").trim();
        if (normalised.isEmpty()) throw new IllegalArgumentException(name + " must not be blank");
        return normalised;
    }

    public UUID getReportId() { return reportId; }
    public UUID getTenantId() { return tenantId; }
    public UUID getSkuId() { return skuId; }
    public String getItemName() { return itemName; }
    public BigDecimal getQuantity() { return quantity; }
    public String getUnit() { return unit; }
    public BigDecimal getEstimatedUnitCostRm() { return estimatedUnitCostRm; }
    public String getReason() { return reason; }
    public UUID getPhotoMediaId() { return photoMediaId; }
}
