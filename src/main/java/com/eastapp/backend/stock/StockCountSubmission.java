package com.eastapp.backend.stock;

import com.eastapp.backend.identity.Tenant;
import com.eastapp.backend.identity.UserAccount;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "stock_count_submissions")
public class StockCountSubmission {
    @Id @Generated @ColumnDefault("uuidv7()")
    @Column(nullable = false, updatable = false)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false, updatable = false)
    private Tenant tenant;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sku_id", nullable = false, updatable = false)
    private StockSku sku;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submitted_by_user_id", nullable = false, updatable = false)
    private UserAccount submittedBy;
    @Column(name = "captured_at", nullable = false, updatable = false)
    private Instant capturedAt;
    @Column(name = "count_cycle_started_at", nullable = false, updatable = false)
    private Instant countCycleStartedAt;
    @Column(name = "stock_photo_name", nullable = false, length = 500)
    private String stockPhotoName;
    @Column(name = "invoice_photo_name", nullable = false, length = 500)
    private String invoicePhotoName;
    @Column(name = "previous_balance_value", nullable = false, precision = 14, scale = 2)
    private BigDecimal previousBalanceValue;
    @Column(name = "current_balance_value", nullable = false, precision = 14, scale = 2)
    private BigDecimal currentBalanceValue;
    @Column(name = "below_minimum_balance", nullable = false)
    private boolean belowMinimumBalance;
    @ElementCollection
    @CollectionTable(name = "stock_count_submission_checks", joinColumns = @JoinColumn(name = "submission_id"))
    @MapKeyColumn(name = "check_key", length = 120)
    @Column(name = "checked", nullable = false)
    private Map<String, Boolean> checkedItems = new LinkedHashMap<>();
    @ElementCollection
    @CollectionTable(name = "stock_count_submission_remarks", joinColumns = @JoinColumn(name = "submission_id"))
    @MapKeyColumn(name = "remark_key", length = 120)
    @Column(name = "remark_value", nullable = false, length = 1000)
    private Map<String, String> remarks = new LinkedHashMap<>();
    @Column(name = "review_status", nullable = false, length = 24)
    private String reviewStatus = "Pending Review";
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_user_id")
    private UserAccount reviewedBy;
    @Column(name = "reviewed_at")
    private Instant reviewedAt;
    @Column(name = "review_note", nullable = false, length = 1000)
    private String reviewNote = "";
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @UpdateTimestamp @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected StockCountSubmission() {}

    public StockCountSubmission(
            Tenant tenant, StockSku sku, UserAccount submittedBy, Instant capturedAt,
            Instant countCycleStartedAt, String stockPhotoName, String invoicePhotoName,
            BigDecimal previousBalanceValue, BigDecimal currentBalanceValue,
            Map<String, Boolean> checkedItems, Map<String, String> remarks
    ) {
        this.tenant = Objects.requireNonNull(tenant);
        this.sku = Objects.requireNonNull(sku);
        this.submittedBy = Objects.requireNonNull(submittedBy);
        this.capturedAt = Objects.requireNonNull(capturedAt);
        this.countCycleStartedAt = Objects.requireNonNull(countCycleStartedAt);
        this.stockPhotoName = text(stockPhotoName);
        this.invoicePhotoName = text(invoicePhotoName);
        this.previousBalanceValue = nonNegative(previousBalanceValue);
        this.currentBalanceValue = nonNegative(currentBalanceValue);
        this.belowMinimumBalance = currentBalanceValue.compareTo(sku.getMinimumBalanceValue()) < 0;
        if (checkedItems != null) this.checkedItems.putAll(checkedItems);
        if (remarks != null) remarks.forEach((key, value) -> this.remarks.put(key, text(value)));
    }

    public void review(String status, String note, UserAccount actor) {
        if (!status.equals("Approved") && !status.equals("Rejected")) {
            throw new IllegalArgumentException("review status must be Approved or Rejected");
        }
        this.reviewStatus = status;
        this.reviewNote = text(note);
        this.reviewedBy = Objects.requireNonNull(actor);
        this.reviewedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public Tenant getTenant() { return tenant; }
    public StockSku getSku() { return sku; }
    public UserAccount getSubmittedBy() { return submittedBy; }
    public Instant getCapturedAt() { return capturedAt; }
    public Instant getCountCycleStartedAt() { return countCycleStartedAt; }
    public String getStockPhotoName() { return stockPhotoName; }
    public String getInvoicePhotoName() { return invoicePhotoName; }
    public BigDecimal getPreviousBalanceValue() { return previousBalanceValue; }
    public BigDecimal getCurrentBalanceValue() { return currentBalanceValue; }
    public boolean isBelowMinimumBalance() { return belowMinimumBalance; }
    public Map<String, Boolean> getCheckedItems() { return checkedItems; }
    public Map<String, String> getRemarks() { return remarks; }
    public String getReviewStatus() { return reviewStatus; }
    public UserAccount getReviewedBy() { return reviewedBy; }
    public Instant getReviewedAt() { return reviewedAt; }
    public String getReviewNote() { return reviewNote; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    private static String text(String value) { return value == null ? "" : value.trim(); }
    private static BigDecimal nonNegative(BigDecimal value) { BigDecimal result = Objects.requireNonNull(value); if (result.signum() < 0) throw new IllegalArgumentException("balance must not be negative"); return result; }
}
