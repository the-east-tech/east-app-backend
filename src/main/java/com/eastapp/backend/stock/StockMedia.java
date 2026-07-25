package com.eastapp.backend.stock;

import com.eastapp.backend.identity.Tenant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Generated;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "stock_media")
public class StockMedia {
    @Id @Generated @ColumnDefault("uuidv7()")
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false, updatable = false)
    private Tenant tenant;

    @Column(name = "storage_key", nullable = false, updatable = false, length = 80)
    private String storageKey;

    @Column(name = "content_type", nullable = false, updatable = false, length = 40)
    private String contentType;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private long sizeBytes;

    @Column(name = "content_bytes", nullable = false, updatable = false, columnDefinition = "bytea")
    private byte[] contentBytes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected StockMedia() {}

    public StockMedia(
            Tenant tenant,
            String storageKey,
            String contentType,
            byte[] contentBytes
    ) {
        this.tenant = Objects.requireNonNull(tenant);
        this.storageKey = Objects.requireNonNull(storageKey);
        this.contentType = Objects.requireNonNull(contentType);
        this.contentBytes = Arrays.copyOf(contentBytes, contentBytes.length);
        this.sizeBytes = contentBytes.length;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public Tenant getTenant() { return tenant; }
    public String getStorageKey() { return storageKey; }
    public String getContentType() { return contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public byte[] getContentBytes() { return Arrays.copyOf(contentBytes, contentBytes.length); }
}
