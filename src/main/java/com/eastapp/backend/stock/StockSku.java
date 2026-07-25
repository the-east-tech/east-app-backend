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
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "stock_skus")
public class StockSku {
    @Id
    @Generated
    @ColumnDefault("uuidv7()")
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false, updatable = false)
    private Tenant tenant;

    @Column(nullable = false, length = 120)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tag1_id", nullable = false)
    private StockTag tag1;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tag2_id", nullable = false)
    private StockTag tag2;

    @Column(nullable = false, length = 32)
    private String unit;

    @Column(name = "minimum_balance_value", nullable = false, precision = 14, scale = 2)
    private BigDecimal minimumBalanceValue;

    @Column(name = "maximum_balance_value", nullable = false, precision = 14, scale = 2)
    private BigDecimal maximumBalanceValue;

    @Column(name = "current_balance_value", nullable = false, precision = 14, scale = 2)
    private BigDecimal currentBalanceValue;

    @Column(name = "recovery_percent", nullable = false)
    private int recoveryPercent;

    @Column(name = "minimum_price_rm", nullable = false, precision = 14, scale = 2)
    private BigDecimal minimumPriceRm;

    @Column(name = "maximum_price_rm", nullable = false, precision = 14, scale = 2)
    private BigDecimal maximumPriceRm;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "stock_sku_suppliers",
            joinColumns = @JoinColumn(name = "sku_id"),
            inverseJoinColumns = @JoinColumn(name = "supplier_id")
    )
    private Set<StockSupplier> suppliers = new LinkedHashSet<>();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "thumbnail_media_id", nullable = false)
    private StockMedia thumbnailMedia;

    @ElementCollection
    @CollectionTable(name = "stock_sku_assignees", joinColumns = @JoinColumn(name = "sku_id"))
    @OrderColumn(name = "position")
    @Column(name = "assigned_staff_name", nullable = false, length = 120)
    private List<String> assignedStaffNames = new ArrayList<>();

    @ElementCollection
    @CollectionTable(
            name = "stock_sku_receiving_checklist",
            joinColumns = @JoinColumn(name = "sku_id")
    )
    @OrderColumn(name = "position")
    @Column(name = "checklist_item", nullable = false, length = 300)
    private List<String> receivingChecklist = new ArrayList<>();

    @Column(name = "stock_check_frequency_days", nullable = false)
    private int stockCheckFrequencyDays;

    @Column(name = "reset_time", nullable = false)
    private LocalTime resetTime;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "cooling_period", nullable = false)
    private boolean coolingPeriod;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "last_updated_by_user_id", nullable = false)
    private UserAccount lastUpdatedBy;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_user_id", nullable = false, updatable = false)
    private UserAccount createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected StockSku() {
    }

    public StockSku(
            Tenant tenant,
            String name,
            StockTag tag1,
            StockTag tag2,
            String unit,
            BigDecimal minimumBalanceValue,
            BigDecimal maximumBalanceValue,
            BigDecimal currentBalanceValue,
            int recoveryPercent,
            BigDecimal minimumPriceRm,
            BigDecimal maximumPriceRm,
            Set<StockSupplier> suppliers,
            StockMedia thumbnailMedia,
            List<String> assignedStaffNames,
            List<String> receivingChecklist,
            int stockCheckFrequencyDays,
            LocalTime resetTime,
            boolean active,
            boolean coolingPeriod,
            UserAccount actor
    ) {
        this.tenant = Objects.requireNonNull(tenant, "tenant must not be null");
        this.createdBy = Objects.requireNonNull(actor, "actor must not be null");
        apply(
                name, tag1, tag2, unit,
                minimumBalanceValue, maximumBalanceValue, currentBalanceValue,
                recoveryPercent, minimumPriceRm, maximumPriceRm,
                suppliers, thumbnailMedia, assignedStaffNames, receivingChecklist,
                stockCheckFrequencyDays, resetTime, active, coolingPeriod, actor
        );
    }

    public void update(
            String name,
            StockTag tag1,
            StockTag tag2,
            String unit,
            BigDecimal minimumBalanceValue,
            BigDecimal maximumBalanceValue,
            BigDecimal currentBalanceValue,
            int recoveryPercent,
            BigDecimal minimumPriceRm,
            BigDecimal maximumPriceRm,
            Set<StockSupplier> suppliers,
            StockMedia thumbnailMedia,
            List<String> assignedStaffNames,
            List<String> receivingChecklist,
            int stockCheckFrequencyDays,
            LocalTime resetTime,
            boolean active,
            boolean coolingPeriod,
            UserAccount actor
    ) {
        apply(
                name, tag1, tag2, unit,
                minimumBalanceValue, maximumBalanceValue, currentBalanceValue,
                recoveryPercent, minimumPriceRm, maximumPriceRm,
                suppliers, thumbnailMedia, assignedStaffNames, receivingChecklist,
                stockCheckFrequencyDays, resetTime, active, coolingPeriod, actor
        );
    }

    private void apply(
            String name,
            StockTag tag1,
            StockTag tag2,
            String unit,
            BigDecimal minimumBalanceValue,
            BigDecimal maximumBalanceValue,
            BigDecimal currentBalanceValue,
            int recoveryPercent,
            BigDecimal minimumPriceRm,
            BigDecimal maximumPriceRm,
            Set<StockSupplier> suppliers,
            StockMedia thumbnailMedia,
            List<String> assignedStaffNames,
            List<String> receivingChecklist,
            int stockCheckFrequencyDays,
            LocalTime resetTime,
            boolean active,
            boolean coolingPeriod,
            UserAccount actor
    ) {
        this.name = requireText(name, "name");
        this.tag1 = requireTenantTag(tag1, "tag1");
        this.tag2 = requireTenantTag(tag2, "tag2");
        this.unit = requireText(unit, "unit");
        this.minimumBalanceValue = nonNegative(minimumBalanceValue, "minimumBalanceValue");
        this.maximumBalanceValue = nonNegative(maximumBalanceValue, "maximumBalanceValue");
        this.currentBalanceValue = nonNegative(currentBalanceValue, "currentBalanceValue");
        if (this.maximumBalanceValue.compareTo(this.minimumBalanceValue) < 0) {
            throw new IllegalArgumentException(
                    "maximumBalanceValue must be at least minimumBalanceValue"
            );
        }
        if (recoveryPercent < 1 || recoveryPercent > 100) {
            throw new IllegalArgumentException("recoveryPercent must be between 1 and 100");
        }
        this.recoveryPercent = recoveryPercent;
        this.minimumPriceRm = nonNegative(minimumPriceRm, "minimumPriceRm");
        this.maximumPriceRm = nonNegative(maximumPriceRm, "maximumPriceRm");
        if (this.maximumPriceRm.compareTo(this.minimumPriceRm) < 0) {
            throw new IllegalArgumentException("maximumPriceRm must be at least minimumPriceRm");
        }
        this.suppliers.clear();
        this.suppliers.addAll(suppliers == null ? Set.of() : suppliers);
        this.thumbnailMedia = requireTenantMedia(thumbnailMedia);
        this.assignedStaffNames.clear();
        if (assignedStaffNames != null) {
            assignedStaffNames.stream()
                    .map(StockSku::text)
                    .filter(item -> !item.isEmpty())
                    .distinct()
                    .forEach(this.assignedStaffNames::add);
        }
        this.receivingChecklist.clear();
        if (receivingChecklist != null) {
            receivingChecklist.stream()
                    .map(StockSku::text)
                    .filter(item -> !item.isEmpty())
                    .forEach(this.receivingChecklist::add);
        }
        if (stockCheckFrequencyDays < 1) {
            throw new IllegalArgumentException("stockCheckFrequencyDays must be positive");
        }
        this.stockCheckFrequencyDays = stockCheckFrequencyDays;
        this.resetTime = Objects.requireNonNull(resetTime, "resetTime must not be null");
        this.active = active;
        this.coolingPeriod = coolingPeriod;
        this.lastUpdatedBy = Objects.requireNonNull(actor, "actor must not be null");
    }

    public void updateBalance(BigDecimal balance, UserAccount actor) {
        this.currentBalanceValue = nonNegative(balance, "balance");
        this.lastUpdatedBy = Objects.requireNonNull(actor, "actor must not be null");
    }

    public UUID getId() {
        return id;
    }

    public Tenant getTenant() {
        return tenant;
    }

    public String getName() {
        return name;
    }

    public StockTag getTag1() {
        return tag1;
    }

    public String getCategory() {
        return tag1.getTag();
    }

    public StockTag getTag2() {
        return tag2;
    }

    public String getLocation() {
        return tag2.getTag();
    }

    public String getUnit() {
        return unit;
    }

    public BigDecimal getMinimumBalanceValue() {
        return minimumBalanceValue;
    }

    public BigDecimal getMaximumBalanceValue() {
        return maximumBalanceValue;
    }

    public BigDecimal getCurrentBalanceValue() {
        return currentBalanceValue;
    }

    public int getRecoveryPercent() {
        return recoveryPercent;
    }

    public BigDecimal getMinimumPriceRm() {
        return minimumPriceRm;
    }

    public BigDecimal getMaximumPriceRm() {
        return maximumPriceRm;
    }

    public Set<StockSupplier> getSuppliers() {
        return suppliers;
    }

    public String getPhotoPath() {
        return thumbnailMedia.getStorageKey();
    }

    public StockMedia getThumbnailMedia() {
        return thumbnailMedia;
    }

    public List<String> getAssignedStaffNames() {
        return assignedStaffNames;
    }

    public List<String> getReceivingChecklist() {
        return receivingChecklist;
    }

    public int getStockCheckFrequencyDays() {
        return stockCheckFrequencyDays;
    }

    public LocalTime getResetTime() {
        return resetTime;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isCoolingPeriod() {
        return coolingPeriod;
    }

    public UserAccount getLastUpdatedBy() {
        return lastUpdatedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }


    private StockMedia requireTenantMedia(StockMedia media) {
        StockMedia resolved = Objects.requireNonNull(media, "thumbnailMedia must not be null");
        if (!resolved.getTenant().getId().equals(tenant.getId())) {
            throw new IllegalArgumentException("thumbnailMedia must belong to the SKU tenant");
        }
        return resolved;
    }

    private StockTag requireTenantTag(StockTag tag, String field) {
        StockTag resolved = Objects.requireNonNull(tag, field + " must not be null");
        if (!resolved.getTenant().getId().equals(tenant.getId())) {
            throw new IllegalArgumentException(field + " must belong to the SKU tenant");
        }
        return resolved;
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }

    private static String requireText(String value, String field) {
        String result = text(value);
        if (result.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return result;
    }

    private static BigDecimal nonNegative(BigDecimal value, String field) {
        BigDecimal result = Objects.requireNonNull(value, field + " must not be null");
        if (result.signum() < 0) {
            throw new IllegalArgumentException(field + " must not be negative");
        }
        return result;
    }
}
