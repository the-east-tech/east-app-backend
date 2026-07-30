package com.eastapp.backend.reports;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "sales_report_details")
public class SalesReportDetail {
    @Id
    @Column(name = "report_id", nullable = false, updatable = false)
    private UUID reportId;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "sales_rm", nullable = false, precision = 14, scale = 2)
    private BigDecimal salesRm;

    @Column(name = "sub_total_rm", nullable = false, precision = 14, scale = 2)
    private BigDecimal subTotalRm;

    @Column(name = "cash_received_by", nullable = false, length = 120)
    private String cashReceivedBy;

    @Column(name = "panda_sales_rm", nullable = false, precision = 14, scale = 2)
    private BigDecimal pandaSalesRm;

    @Column(name = "staff_count", nullable = false)
    private int staffCount;

    protected SalesReportDetail() {
    }

    public SalesReportDetail(
            UUID reportId,
            UUID tenantId,
            BigDecimal salesRm,
            BigDecimal subTotalRm,
            String cashReceivedBy,
            BigDecimal pandaSalesRm,
            int staffCount
    ) {
        this.reportId = Objects.requireNonNull(reportId, "reportId must not be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        update(salesRm, subTotalRm, cashReceivedBy, pandaSalesRm, staffCount);
    }

    public void update(
            BigDecimal salesRm,
            BigDecimal subTotalRm,
            String cashReceivedBy,
            BigDecimal pandaSalesRm,
            int staffCount
    ) {
        this.salesRm = nonNegative(salesRm, "salesRm");
        this.subTotalRm = nonNegative(subTotalRm, "subTotalRm");
        this.cashReceivedBy = requiredText(cashReceivedBy, "cashReceivedBy");
        this.pandaSalesRm = nonNegative(pandaSalesRm, "pandaSalesRm");
        if (staffCount < 1 || staffCount > 500) {
            throw new IllegalArgumentException("staffCount must be between 1 and 500");
        }
        this.staffCount = staffCount;
    }

    private static BigDecimal nonNegative(BigDecimal value, String name) {
        BigDecimal required = Objects.requireNonNull(value, name + " must not be null");
        if (required.signum() < 0) throw new IllegalArgumentException(name + " must not be negative");
        return required;
    }

    private static String requiredText(String value, String name) {
        String required = Objects.requireNonNull(value, name + " must not be null").trim();
        if (required.isEmpty()) throw new IllegalArgumentException(name + " must not be blank");
        return required;
    }

    public BigDecimal grossSalesRm() {
        return salesRm.add(pandaSalesRm);
    }

    public UUID getReportId() { return reportId; }
    public UUID getTenantId() { return tenantId; }
    public BigDecimal getSalesRm() { return salesRm; }
    public BigDecimal getSubTotalRm() { return subTotalRm; }
    public String getCashReceivedBy() { return cashReceivedBy; }
    public BigDecimal getPandaSalesRm() { return pandaSalesRm; }
    public int getStaffCount() { return staffCount; }
}
