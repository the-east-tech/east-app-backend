package com.eastapp.backend.tasks;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Generated;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "daily_task_audit_entries")
public class DailyTaskAuditEntry {
    @Id
    @Generated
    @ColumnDefault("uuidv7()")
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "template_id", updatable = false)
    private UUID templateId;

    @Column(name = "record_id", updatable = false)
    private UUID recordId;

    @Column(name = "actor_user_id", nullable = false, updatable = false)
    private UUID actorUserId;

    @Column(nullable = false, length = 48, updatable = false)
    private String action;

    @Column(nullable = false, length = 1200, updatable = false)
    private String details;

    @CreationTimestamp
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected DailyTaskAuditEntry() {
    }

    public DailyTaskAuditEntry(
            UUID tenantId,
            UUID templateId,
            UUID recordId,
            UUID actorUserId,
            String action,
            String details
    ) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        if (templateId == null && recordId == null) throw new IllegalArgumentException("An audit target is required");
        this.templateId = templateId;
        this.recordId = recordId;
        this.actorUserId = Objects.requireNonNull(actorUserId, "actorUserId must not be null");
        this.action = requireText(action, "action");
        this.details = details == null ? "" : details.trim();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getTemplateId() { return templateId; }
    public UUID getRecordId() { return recordId; }
    public UUID getActorUserId() { return actorUserId; }
    public String getAction() { return action; }
    public String getDetails() { return details; }
    public Instant getOccurredAt() { return occurredAt; }

    private static String requireText(String value, String field) {
        String normalised = Objects.requireNonNull(value, field + " must not be null").trim();
        if (normalised.isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
        return normalised;
    }
}
