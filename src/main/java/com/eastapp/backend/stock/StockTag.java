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

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "stock_tags")
public class StockTag {
    @Id @Generated @ColumnDefault("uuidv7()")
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false, updatable = false)
    private Tenant tenant;

    @Column(nullable = false, length = 80)
    private String tag;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_user_id", nullable = false, updatable = false)
    private UserAccount createdBy;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "updated_by_user_id", nullable = false)
    private UserAccount updatedBy;

    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected StockTag() {}

    public StockTag(Tenant tenant, String tag, UserAccount actor) {
        this.tenant = Objects.requireNonNull(tenant);
        this.tag = requireText(tag, "tag");
        this.createdBy = Objects.requireNonNull(actor);
        this.updatedBy = actor;
    }

    public void rename(String tag, UserAccount actor) {
        this.tag = requireText(tag, "tag");
        this.updatedBy = Objects.requireNonNull(actor);
    }

    public UUID getId() { return id; }
    public Tenant getTenant() { return tenant; }
    public String getTag() { return tag; }
    public UserAccount getCreatedBy() { return createdBy; }
    public UserAccount getUpdatedBy() { return updatedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    private static String requireText(String value, String field) {
        String result = Objects.requireNonNull(value, field + " must not be null").trim();
        if (result.isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
        return result;
    }
}
