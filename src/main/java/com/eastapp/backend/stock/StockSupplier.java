package com.eastapp.backend.stock;

import com.eastapp.backend.organisation.Tenant;
import com.eastapp.backend.people.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "stock_suppliers")
public class StockSupplier {
    public static final String DEFAULT_PURCHASE_MESSAGE_TEMPLATE = """
            Hi, please prepare the following items:

            {items}

            {date}
            Please confirm availability and delivery time. Thank u.
            """.trim();

    public static final String ORDER_NONE = "NONE";
    public static final String ORDERED = "ORDERED";
    public static final String ORDER_SUBMITTED = "SUBMITTED";
    public static final String ORDER_CORRECTION_REQUIRED = "CORRECTION_REQUIRED";

    @Id @Generated @ColumnDefault("uuidv7()")
    @Column(nullable = false, updatable = false)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false, updatable = false)
    private Tenant tenant;
    @Column(name = "supplier_name", nullable = false, length = 120)
    private String supplierName;
    @Column(name = "supplier_item", nullable = false, length = 160)
    private String supplierItem;
    @Column(name = "contact_person", nullable = false, length = 120)
    private String contactPerson;
    @Column(nullable = false, length = 32)
    private String phone;
    @Column(nullable = false, length = 500)
    private String address;
    @Column(nullable = false, length = 1000)
    private String notes;
    @Column(nullable = false, length = 32)
    private String unit;
    @Column(name = "recommended_purchase_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal recommendedPurchaseAmount;
    @Column(name = "recommended_purchase_frequency", nullable = false, length = 80)
    private String recommendedPurchaseFrequency;
    @Column(name = "pricing_per_unit", nullable = false, precision = 14, scale = 2)
    private BigDecimal pricingPerUnit;
    @Column(name = "minimum_balance_value", nullable = false, precision = 14, scale = 2)
    private BigDecimal minimumBalanceValue;
    @Column(name = "maximum_balance_value", nullable = false, precision = 14, scale = 2)
    private BigDecimal maximumBalanceValue;
    @Column(name = "current_balance_value", nullable = false, precision = 14, scale = 2)
    private BigDecimal currentBalanceValue;
    @Column(name = "last_balance_updated_at", nullable = false)
    private Instant lastBalanceUpdatedAt;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "last_balance_updated_by_user_id", nullable = false)
    private UserAccount lastBalanceUpdatedBy;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_user_id", nullable = false, updatable = false)
    private UserAccount createdBy;

    @Column(name = "purchase_message_template", nullable = false, length = 2000)
    private String purchaseMessageTemplate = DEFAULT_PURCHASE_MESSAGE_TEMPLATE;
    @Column(name = "order_state", nullable = false, length = 24)
    private String orderState = ORDER_NONE;
    @Column(name = "current_order_reference")
    private UUID currentOrderReference;
    @Column(name = "ordered_at")
    private Instant orderedAt;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ordered_by_user_id")
    private UserAccount orderedBy;
    @Column(name = "ordered_message", nullable = false, length = 4000)
    private String orderedMessage = "";

    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @UpdateTimestamp @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected StockSupplier() {}

    public StockSupplier(
            Tenant tenant, String supplierName, String supplierItem, String contactPerson,
            String phone, String address, String notes, String unit,
            BigDecimal recommendedPurchaseAmount, String recommendedPurchaseFrequency,
            BigDecimal pricingPerUnit, BigDecimal minimumBalanceValue,
            BigDecimal maximumBalanceValue, BigDecimal currentBalanceValue,
            UserAccount actor
    ) {
        this.tenant = Objects.requireNonNull(tenant);
        this.supplierName = requireText(supplierName, "supplierName");
        this.supplierItem = requireText(supplierItem, "supplierItem");
        this.contactPerson = text(contactPerson);
        this.phone = text(phone);
        this.address = text(address);
        this.notes = text(notes);
        this.unit = requireText(unit, "unit");
        this.recommendedPurchaseAmount = nonNegative(recommendedPurchaseAmount, "recommendedPurchaseAmount");
        this.recommendedPurchaseFrequency = text(recommendedPurchaseFrequency);
        this.pricingPerUnit = nonNegative(pricingPerUnit, "pricingPerUnit");
        this.minimumBalanceValue = nonNegative(minimumBalanceValue, "minimumBalanceValue");
        this.maximumBalanceValue = nonNegative(maximumBalanceValue, "maximumBalanceValue");
        this.currentBalanceValue = nonNegative(currentBalanceValue, "currentBalanceValue");
        validateRanges();
        this.lastBalanceUpdatedAt = Instant.now();
        this.lastBalanceUpdatedBy = Objects.requireNonNull(actor);
        this.createdBy = actor;
    }

    public void update(
            String supplierName, String supplierItem, String contactPerson,
            String phone, String address, String notes, String unit,
            BigDecimal recommendedPurchaseAmount, String recommendedPurchaseFrequency,
            BigDecimal pricingPerUnit, BigDecimal minimumBalanceValue,
            BigDecimal maximumBalanceValue, BigDecimal currentBalanceValue,
            UserAccount actor
    ) {
        this.supplierName = requireText(supplierName, "supplierName");
        this.supplierItem = requireText(supplierItem, "supplierItem");
        this.contactPerson = text(contactPerson);
        this.phone = text(phone);
        this.address = text(address);
        this.notes = text(notes);
        this.unit = requireText(unit, "unit");
        this.recommendedPurchaseAmount = nonNegative(recommendedPurchaseAmount, "recommendedPurchaseAmount");
        this.recommendedPurchaseFrequency = text(recommendedPurchaseFrequency);
        this.pricingPerUnit = nonNegative(pricingPerUnit, "pricingPerUnit");
        this.minimumBalanceValue = nonNegative(minimumBalanceValue, "minimumBalanceValue");
        this.maximumBalanceValue = nonNegative(maximumBalanceValue, "maximumBalanceValue");
        this.currentBalanceValue = nonNegative(currentBalanceValue, "currentBalanceValue");
        validateRanges();
        this.lastBalanceUpdatedAt = Instant.now();
        this.lastBalanceUpdatedBy = Objects.requireNonNull(actor);
    }

    public void updateBalance(BigDecimal balance, UserAccount actor) {
        this.currentBalanceValue = nonNegative(balance, "balance");
        this.lastBalanceUpdatedAt = Instant.now();
        this.lastBalanceUpdatedBy = Objects.requireNonNull(actor);
    }

    public void updatePurchaseMessageTemplate(String template) {
        String value = requireText(template, "messageTemplate");
        if (value.length() > 2000) {
            throw new IllegalArgumentException("messageTemplate must not exceed 2000 characters");
        }
        this.purchaseMessageTemplate = value;
    }

    public void markOrdered(String message, UserAccount actor, Instant when) {
        if (!ORDER_NONE.equals(orderState)) {
            throw new IllegalStateException("This supplier already has an active order.");
        }
        String value = requireText(message, "message");
        if (value.length() > 4000) {
            throw new IllegalArgumentException("message must not exceed 4000 characters");
        }
        this.orderState = ORDERED;
        this.currentOrderReference = UUID.randomUUID();
        this.orderedAt = Objects.requireNonNull(when, "when must not be null");
        this.orderedBy = Objects.requireNonNull(actor, "actor must not be null");
        this.orderedMessage = value;
    }

    public UUID beginReceiving() {
        if (!canReceive()) {
            throw new IllegalArgumentException("Mark this supplier as Ordered Done before receiving stock.");
        }
        if (currentOrderReference == null) {
            throw new IllegalArgumentException("Active supplier order is missing its reference.");
        }
        this.orderState = ORDER_SUBMITTED;
        return currentOrderReference;
    }

    public void applyReceivingReview(UUID orderReference, StockWorkflowStatus status) {
        if (orderReference == null || !orderReference.equals(currentOrderReference)) return;
        if (!ORDER_SUBMITTED.equals(orderState)) return;
        if (status == StockWorkflowStatus.DONE) {
            orderState = ORDER_NONE;
            currentOrderReference = null;
        } else if (status == StockWorkflowStatus.PENDING) {
            orderState = ORDER_CORRECTION_REQUIRED;
        }
    }

    public boolean canReceive() {
        return ORDERED.equals(orderState) || ORDER_CORRECTION_REQUIRED.equals(orderState);
    }

    private void validateRanges() {
        if (maximumBalanceValue.compareTo(minimumBalanceValue) < 0) {
            throw new IllegalArgumentException("maximumBalanceValue must be at least minimumBalanceValue");
        }
    }

    public UUID getId() { return id; }
    public Tenant getTenant() { return tenant; }
    public String getSupplierName() { return supplierName; }
    public String getSupplierItem() { return supplierItem; }
    public String getContactPerson() { return contactPerson; }
    public String getPhone() { return phone; }
    public String getAddress() { return address; }
    public String getNotes() { return notes; }
    public String getUnit() { return unit; }
    public BigDecimal getRecommendedPurchaseAmount() { return recommendedPurchaseAmount; }
    public String getRecommendedPurchaseFrequency() { return recommendedPurchaseFrequency; }
    public BigDecimal getPricingPerUnit() { return pricingPerUnit; }
    public BigDecimal getMinimumBalanceValue() { return minimumBalanceValue; }
    public BigDecimal getMaximumBalanceValue() { return maximumBalanceValue; }
    public BigDecimal getCurrentBalanceValue() { return currentBalanceValue; }
    public Instant getLastBalanceUpdatedAt() { return lastBalanceUpdatedAt; }
    public UserAccount getLastBalanceUpdatedBy() { return lastBalanceUpdatedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getPurchaseMessageTemplate() { return purchaseMessageTemplate; }
    public String getOrderState() { return orderState; }
    public UUID getCurrentOrderReference() { return currentOrderReference; }
    public Instant getOrderedAt() { return orderedAt; }
    public UserAccount getOrderedBy() { return orderedBy; }
    public String getOrderedMessage() { return orderedMessage; }

    private static String text(String value) { return value == null ? "" : value.trim(); }
    private static String requireText(String value, String field) {
        String result = text(value);
        if (result.isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
        return result;
    }
    private static BigDecimal nonNegative(BigDecimal value, String field) {
        BigDecimal result = Objects.requireNonNull(value, field + " must not be null");
        if (result.signum() < 0) throw new IllegalArgumentException(field + " must not be negative");
        return result;
    }
}
