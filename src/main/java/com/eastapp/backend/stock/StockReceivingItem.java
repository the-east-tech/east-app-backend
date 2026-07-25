package com.eastapp.backend.stock;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Generated;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "stock_receiving_items")
public class StockReceivingItem {
    @Id @Generated @ColumnDefault("uuidv7()")
    @Column(nullable = false, updatable = false)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receiving_id", nullable = false, updatable = false)
    private StockReceiving receiving;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sku_id", nullable = false, updatable = false)
    private StockSku sku;
    @Column(nullable = false, updatable = false)
    private int position;
    @Column(name = "sku_name", nullable = false, length = 120)
    private String skuName;
    @Column(name = "invoice_quantity", nullable = false, precision = 14, scale = 2)
    private BigDecimal invoiceQuantity;
    @Column(name = "received_quantity", nullable = false, precision = 14, scale = 2)
    private BigDecimal receivedQuantity;
    @Column(nullable = false, length = 32)
    private String unit;
    @Column(nullable = false, length = 80)
    private String condition;
    @Column(nullable = false, length = 1000)
    private String note;

    protected StockReceivingItem() {}

    public StockReceivingItem(
            StockSku sku, BigDecimal invoiceQuantity, BigDecimal receivedQuantity,
            String condition, String note
    ) {
        this.sku = Objects.requireNonNull(sku);
        this.skuName = sku.getName();
        this.invoiceQuantity = nonNegative(invoiceQuantity);
        this.receivedQuantity = nonNegative(receivedQuantity);
        this.unit = sku.getUnit();
        this.condition = text(condition);
        this.note = text(note);
    }

    void attachTo(StockReceiving receiving, int position) {
        if (position < 0) throw new IllegalArgumentException("position must not be negative");
        this.receiving = Objects.requireNonNull(receiving);
        this.position = position;
    }
    public UUID getId() { return id; }
    public int getPosition() { return position; }
    public StockSku getSku() { return sku; }
    public String getSkuName() { return skuName; }
    public BigDecimal getInvoiceQuantity() { return invoiceQuantity; }
    public BigDecimal getReceivedQuantity() { return receivedQuantity; }
    public String getUnit() { return unit; }
    public String getCondition() { return condition; }
    public String getNote() { return note; }
    private static String text(String value) { return value == null ? "" : value.trim(); }
    private static BigDecimal nonNegative(BigDecimal value) { BigDecimal result = Objects.requireNonNull(value); if (result.signum() < 0) throw new IllegalArgumentException("quantity must not be negative"); return result; }
}
