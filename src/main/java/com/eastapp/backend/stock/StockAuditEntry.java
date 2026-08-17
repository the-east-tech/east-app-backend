package com.eastapp.backend.stock;

import com.eastapp.backend.organisation.Tenant;
import com.eastapp.backend.auth.security.AuthenticatedUser;
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
import org.hibernate.annotations.Generated;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "stock_audit_entries")
public class StockAuditEntry {
    @Id @Generated @ColumnDefault("uuidv7()")
    @Column(nullable = false, updatable = false)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false, updatable = false)
    private Tenant tenant;
    @Column(nullable = false, length = 80)
    private String module;
    @Column(nullable = false, length = 120)
    private String action;
    @Column(name = "item_id")
    private UUID itemId;
    @Column(name = "item_name", nullable = false, length = 160)
    private String itemName;
    @Column(name = "actor_name", nullable = false, length = 120)
    private String actorName;
    @Column(name = "actor_employee_id", nullable = false, length = 32)
    private String actorEmployeeId;
    @Column(name = "actor_role", nullable = false, length = 80)
    private String actorRole;
    @Column(name = "captured_at", nullable = false, updatable = false)
    private Instant capturedAt;
    @OneToMany(mappedBy = "entry", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<StockAuditChange> changes = new ArrayList<>();
    @Column(nullable = false, length = 1000)
    private String note;

    protected StockAuditEntry() {}

    public StockAuditEntry(
            Tenant tenant, String module, String action, UUID itemId,
            String itemName, AuthenticatedUser actor, String note
    ) {
        this.tenant = Objects.requireNonNull(tenant);
        this.module = text(module);
        this.action = text(action);
        this.itemId = itemId;
        this.itemName = text(itemName);
        this.actorName = actor.fullName();
        this.actorEmployeeId = actor.employeeId();
        this.actorRole = actor.systemRole().name();
        this.capturedAt = Instant.now();
        this.note = text(note);
    }

    public StockAuditEntry addChange(String field, Object oldValue, Object newValue) {
        String oldText = String.valueOf(oldValue == null ? "-" : oldValue);
        String newText = String.valueOf(newValue == null ? "-" : newValue);
        if (!oldText.equals(newText)) {
            StockAuditChange change = new StockAuditChange(changes.size(), field, oldText, newText);
            change.attachTo(this);
            changes.add(change);
        }
        return this;
    }

    public UUID getId() { return id; }
    public String getModule() { return module; }
    public String getAction() { return action; }
    public UUID getItemId() { return itemId; }
    public String getItemName() { return itemName; }
    public String getActorName() { return actorName; }
    public String getActorEmployeeId() { return actorEmployeeId; }
    public String getActorRole() { return actorRole; }
    public Instant getCapturedAt() { return capturedAt; }
    public List<StockAuditChange> getChanges() { return changes; }
    public String getNote() { return note; }
    private static String text(String value) { return value == null ? "" : value.trim(); }
}
