package com.eastapp.backend.activity;

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
@Table(name = "activity_events")
public class ActivityEvent {
    @Id
    @Generated
    @ColumnDefault("uuidv7()")
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "actor_user_id", nullable = false, updatable = false)
    private UUID actorUserId;

    @Column(name = "actor_name", nullable = false, length = 120, updatable = false)
    private String actorName;

    @Column(name = "actor_employee_id", nullable = false, length = 32, updatable = false)
    private String actorEmployeeId;

    @Column(name = "actor_role", nullable = false, length = 80, updatable = false)
    private String actorRole;

    @Column(nullable = false, length = 64, updatable = false)
    private String module;

    @Column(nullable = false, length = 64, updatable = false)
    private String action;

    @Column(name = "entity_type", nullable = false, length = 100, updatable = false)
    private String entityType;

    @Column(nullable = false, length = 240, updatable = false)
    private String subject;

    @Column(nullable = false, length = 2000, updatable = false)
    private String detail;

    @Column(name = "target_id", updatable = false)
    private UUID targetId;

    @Column(nullable = false, length = 240, updatable = false)
    private String route;

    @CreationTimestamp
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected ActivityEvent() {
    }

    public ActivityEvent(
            UUID tenantId,
            UUID actorUserId,
            String actorName,
            String actorEmployeeId,
            String actorRole,
            String module,
            String action,
            String entityType,
            String subject,
            String detail,
            UUID targetId,
            String route
    ) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.actorUserId = Objects.requireNonNull(actorUserId, "actorUserId must not be null");
        this.actorName = requireText(actorName, "actorName");
        this.actorEmployeeId = requireText(actorEmployeeId, "actorEmployeeId");
        this.actorRole = requireText(actorRole, "actorRole");
        this.module = requireText(module, "module");
        this.action = requireText(action, "action");
        this.entityType = requireText(entityType, "entityType");
        this.subject = optionalText(subject, 240);
        this.detail = optionalText(detail, 2000);
        this.targetId = targetId;
        this.route = requireText(route, "route");
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getActorUserId() { return actorUserId; }
    public String getActorName() { return actorName; }
    public String getActorEmployeeId() { return actorEmployeeId; }
    public String getActorRole() { return actorRole; }
    public String getModule() { return module; }
    public String getAction() { return action; }
    public String getEntityType() { return entityType; }
    public String getSubject() { return subject; }
    public String getDetail() { return detail; }
    public UUID getTargetId() { return targetId; }
    public String getRoute() { return route; }
    public Instant getOccurredAt() { return occurredAt; }

    public String summary() {
        return actorName + " " + action + " "
                + (subject.isBlank() ? entityType : subject);
    }

    private static String requireText(String value, String fieldName) {
        String normalised = Objects.requireNonNull(value, fieldName + " must not be null").trim();
        if (normalised.isEmpty()) throw new IllegalArgumentException(fieldName + " must not be blank");
        return normalised;
    }

    private static String optionalText(String value, int maximumLength) {
        if (value == null) return "";
        String normalised = value.trim();
        return normalised.substring(0, Math.min(normalised.length(), maximumLength));
    }
}
