package com.eastapp.backend.tasks;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "task_templates")
public class TaskTemplate {
    @Id
    @Generated
    @ColumnDefault("uuidv7()")
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "tag_id", nullable = false)
    private UUID tagId;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(nullable = false, length = 1000)
    private String instruction;

    @Column(name = "required_photo_count", nullable = false)
    private int requiredPhotoCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "schedule_type", nullable = false, length = 16)
    private TaskScheduleType scheduleType;

    @Column(name = "first_task_date", nullable = false)
    private LocalDate firstTaskDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_by_user_id", nullable = false, updatable = false)
    private UUID createdByUserId;

    @Column(name = "updated_by_user_id", nullable = false)
    private UUID updatedByUserId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TaskTemplate() {
    }

    public TaskTemplate(
            UUID tenantId,
            UUID tagId,
            String title,
            String instruction,
            int requiredPhotoCount,
            TaskScheduleType scheduleType,
            LocalDate firstTaskDate,
            LocalDate endDate,
            boolean active,
            UUID actorUserId
    ) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.createdByUserId = Objects.requireNonNull(actorUserId, "actorUserId must not be null");
        update(
                tagId, title, instruction, requiredPhotoCount,
                scheduleType, firstTaskDate, endDate, active, actorUserId
        );
    }

    public void update(
            UUID tagId,
            String title,
            String instruction,
            int requiredPhotoCount,
            TaskScheduleType scheduleType,
            LocalDate firstTaskDate,
            LocalDate endDate,
            boolean active,
            UUID actorUserId
    ) {
        this.tagId = Objects.requireNonNull(tagId, "tagId must not be null");
        this.title = requireText(title, "title");
        this.instruction = optionalText(instruction);
        if (requiredPhotoCount < 1 || requiredPhotoCount > 40) {
            throw new IllegalArgumentException("requiredPhotoCount must be between 1 and 40");
        }
        this.requiredPhotoCount = requiredPhotoCount;
        this.scheduleType = Objects.requireNonNull(scheduleType, "scheduleType must not be null");
        this.firstTaskDate = Objects.requireNonNull(firstTaskDate, "firstTaskDate must not be null");
        if (endDate != null && endDate.isBefore(firstTaskDate)) {
            throw new IllegalArgumentException("endDate must not be before firstTaskDate");
        }
        this.endDate = endDate;
        this.active = active;
        this.updatedByUserId = Objects.requireNonNull(actorUserId, "actorUserId must not be null");
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getTagId() { return tagId; }
    public String getTitle() { return title; }
    public String getInstruction() { return instruction; }
    public int getRequiredPhotoCount() { return requiredPhotoCount; }
    public TaskScheduleType getScheduleType() { return scheduleType; }
    public LocalDate getFirstTaskDate() { return firstTaskDate; }
    public LocalDate getEndDate() { return endDate; }
    public boolean isActive() { return active; }
    public UUID getCreatedByUserId() { return createdByUserId; }
    public UUID getUpdatedByUserId() { return updatedByUserId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public boolean isScheduledFor(LocalDate date) {
        Objects.requireNonNull(date, "date must not be null");
        if (date.isBefore(firstTaskDate) || endDate != null && date.isAfter(endDate)) {
            return false;
        }
        long elapsedDays = ChronoUnit.DAYS.between(firstTaskDate, date);
        return switch (scheduleType) {
            case AD_HOC -> date.equals(firstTaskDate);
            case DAILY -> true;
            case WEEKLY -> elapsedDays % 7 == 0;
            case BIWEEKLY -> elapsedDays % 14 == 0;
            case MONTHLY -> date.getDayOfMonth()
                    == Math.min(firstTaskDate.getDayOfMonth(), date.lengthOfMonth());
        };
    }

    private static String requireText(String value, String field) {
        String normalised = Objects.requireNonNull(value, field + " must not be null").trim();
        if (normalised.isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
        return normalised;
    }

    private static String optionalText(String value) {
        return value == null ? "" : value.trim();
    }
}
