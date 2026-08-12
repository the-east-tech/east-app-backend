package com.eastapp.backend.stock;

import com.eastapp.backend.organisation.Tenant;
import com.eastapp.backend.people.UserAccount;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "stock_receivings")
public class StockReceiving {
    @Id @Generated @ColumnDefault("uuidv7()")
    @Column(nullable = false, updatable = false)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false, updatable = false)
    private Tenant tenant;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id", nullable = false, updatable = false)
    private StockSupplier supplier;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "received_by_user_id", nullable = false, updatable = false)
    private UserAccount receivedBy;
    @Column(name = "captured_at", nullable = false, updatable = false)
    private Instant capturedAt;
    @Column(name = "invoice_photo_name", nullable = false, length = 500)
    private String invoicePhotoName;
    @Column(name = "goods_photo_name", nullable = false, length = 500)
    private String goodsPhotoName;
    @OneToMany(mappedBy = "receiving", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<StockReceivingItem> items = new ArrayList<>();
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

    protected StockReceiving() {}

    public StockReceiving(
            Tenant tenant, StockSupplier supplier, UserAccount receivedBy,
            Instant capturedAt, String invoicePhotoName, String goodsPhotoName
    ) {
        this.tenant = Objects.requireNonNull(tenant);
        this.supplier = Objects.requireNonNull(supplier);
        this.receivedBy = Objects.requireNonNull(receivedBy);
        this.capturedAt = Objects.requireNonNull(capturedAt);
        this.invoicePhotoName = text(invoicePhotoName);
        this.goodsPhotoName = text(goodsPhotoName);
    }

    public void addItem(StockReceivingItem item) {
        item.attachTo(this, items.size());
        items.add(item);
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
    public StockSupplier getSupplier() { return supplier; }
    public UserAccount getReceivedBy() { return receivedBy; }
    public Instant getCapturedAt() { return capturedAt; }
    public String getInvoicePhotoName() { return invoicePhotoName; }
    public String getGoodsPhotoName() { return goodsPhotoName; }
    public List<StockReceivingItem> getItems() { return items; }
    public String getReviewStatus() { return reviewStatus; }
    public UserAccount getReviewedBy() { return reviewedBy; }
    public Instant getReviewedAt() { return reviewedAt; }
    public String getReviewNote() { return reviewNote; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    private static String text(String value) { return value == null ? "" : value.trim(); }
}
