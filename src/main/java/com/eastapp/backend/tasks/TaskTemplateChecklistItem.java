package com.eastapp.backend.tasks;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Generated;

import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "task_template_checklist_items")
public class TaskTemplateChecklistItem {
    @Id
    @Generated
    @ColumnDefault("uuidv7()")
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "template_id", nullable = false, updatable = false)
    private UUID templateId;

    @Column(nullable = false, updatable = false)
    private int position;

    @Column(nullable = false, length = 300, updatable = false)
    private String description;

    protected TaskTemplateChecklistItem() {
    }

    public TaskTemplateChecklistItem(
            UUID tenantId,
            UUID templateId,
            int position,
            String description
    ) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.templateId = Objects.requireNonNull(templateId, "templateId must not be null");
        if (position < 0 || position > 4) throw new IllegalArgumentException("position must be between 0 and 4");
        this.position = position;
        this.description = requireText(description);
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getTemplateId() { return templateId; }
    public int getPosition() { return position; }
    public String getDescription() { return description; }

    private static String requireText(String value) {
        String normalised = Objects.requireNonNull(value, "description must not be null").trim();
        if (normalised.isEmpty()) throw new IllegalArgumentException("description must not be blank");
        return normalised;
    }
}
