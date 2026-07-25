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

import java.util.UUID;

@Entity
@Table(name = "stock_audit_changes")
public class StockAuditChange {
    @Id @Generated @ColumnDefault("uuidv7()")
    @Column(nullable = false, updatable = false)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "audit_entry_id", nullable = false, updatable = false)
    private StockAuditEntry entry;
    @Column(nullable = false)
    private int position;
    @Column(name = "field_name", nullable = false, length = 120)
    private String field;
    @Column(name = "old_value", nullable = false, length = 1000)
    private String oldValue;
    @Column(name = "new_value", nullable = false, length = 1000)
    private String newValue;

    protected StockAuditChange() {}
    public StockAuditChange(int position, String field, String oldValue, String newValue) {
        this.position = position;
        this.field = field;
        this.oldValue = oldValue;
        this.newValue = newValue;
    }
    void attachTo(StockAuditEntry entry) { this.entry = entry; }
    public int getPosition() { return position; }
    public String getField() { return field; }
    public String getOldValue() { return oldValue; }
    public String getNewValue() { return newValue; }
}
