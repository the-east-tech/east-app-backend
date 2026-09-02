package com.eastapp.backend.reports;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "sales_report_details")
public class SalesReportDetail {
    private static final BigDecimal FOOD_DELIVERY_NET_RATE = new BigDecimal("0.60");
    @Id
    @Column(name = "report_id", nullable = false, updatable = false)
    private UUID reportId;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "sales_rm", nullable = false, precision = 14, scale = 2)
    private BigDecimal salesRm;

    @Column(name = "sub_total_rm", nullable = false, precision = 14, scale = 2)
    private BigDecimal subTotalRm;

    @Column(name = "cash_received_by_user_id", nullable = false)
    private UUID cashReceivedByUserId;

    @Column(name = "cash_received_by", nullable = false, length = 120)
    private String cashReceivedBy;

    @Column(name = "panda_sales_rm", nullable = false, precision = 14, scale = 2)
    private BigDecimal pandaSalesRm;

    @Column(name = "ewallet_total_rm", nullable = false, precision = 14, scale = 2)
    private BigDecimal ewalletTotalRm;

    @Column(name = "staff_count", nullable = false)
    private int staffCount;

    protected SalesReportDetail() {
    }

    public SalesReportDetail(
            UUID reportId,
            UUID tenantId,
            BigDecimal subTotalRm,
            UUID cashReceivedByUserId,
            String cashReceivedBy,
            BigDecimal pandaSalesRm,
            BigDecimal ewalletTotalRm,
            int staffCount
    ) {
        this.reportId = Objects.requireNonNull(reportId, "reportId must not be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        update(
                subTotalRm,
                cashReceivedByUserId,
                cashReceivedBy,
                pandaSalesRm,
                ewalletTotalRm,
                staffCount
        );
    }

    public void update(
            BigDecimal subTotalRm,
            UUID cashReceivedByUserId,
            String cashReceivedBy,
            BigDecimal pandaSalesRm,
            BigDecimal ewalletTotalRm,
            int staffCount
    ) {
        this.subTotalRm = nonNegative(subTotalRm, "subTotalRm");
        this.cashReceivedByUserId = Objects.requireNonNull(
                cashReceivedByUserId,
                "cashReceivedByUserId must not be null"
        );
        this.cashReceivedBy = requiredText(cashReceivedBy, "cashReceivedBy");
        this.pandaSalesRm = nonNegative(pandaSalesRm, "pandaSalesRm");
        this.ewalletTotalRm = nonNegative(ewalletTotalRm, "ewalletTotalRm");
        this.salesRm = this.subTotalRm
                .add(netFoodDeliverySalesRm())
                .add(this.ewalletTotalRm)
                .setScale(2, RoundingMode.HALF_UP);
        if (staffCount < 1 || staffCount > 500) {
            throw new IllegalArgumentException("staffCount must be between 1 and 500");
        }
        this.staffCount = staffCount;
    }

    private static BigDecimal nonNegative(BigDecimal value, String name) {
        BigDecimal required = Objects.requireNonNull(value, name + " must not be null");
        if (required.signum() < 0) throw new IllegalArgumentException(name + " must not be negative");
        return required.setScale(2, RoundingMode.HALF_UP);
    }

    private static String requiredText(String value, String name) {
        String required = Objects.requireNonNull(value, name + " must not be null").trim();
        if (required.isEmpty()) throw new IllegalArgumentException(name + " must not be blank");
        return required;
    }

    public BigDecimal grossSalesRm() {
        return subTotalRm.add(pandaSalesRm).add(ewalletTotalRm).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal recognisedSalesRm() {
        return salesRm;
    }

    public BigDecimal grossFoodDeliverySalesRm() {
        return pandaSalesRm;
    }

    public BigDecimal netFoodDeliverySalesRm() {
        return pandaSalesRm.multiply(FOOD_DELIVERY_NET_RATE).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal estimatedPlatformCommissionRm() {
        return pandaSalesRm.subtract(netFoodDeliverySalesRm()).setScale(2, RoundingMode.HALF_UP);
    }

    public UUID getReportId() { return reportId; }
    public UUID getTenantId() { return tenantId; }
    public BigDecimal getSalesRm() { return salesRm; }
    public BigDecimal getSubTotalRm() { return subTotalRm; }
    public UUID getCashReceivedByUserId() { return cashReceivedByUserId; }
    public String getCashReceivedBy() { return cashReceivedBy; }
    public BigDecimal getPandaSalesRm() { return pandaSalesRm; }
    public BigDecimal getEwalletTotalRm() { return ewalletTotalRm; }
    public int getStaffCount() { return staffCount; }
}
