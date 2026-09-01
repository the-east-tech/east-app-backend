package com.eastapp.backend.tasks;

import com.eastapp.backend.people.SystemRole;
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
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "task_records")
public class TaskRecord {
    @Id
    @Generated
    @ColumnDefault("uuidv7()")
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "template_id", nullable = false, updatable = false)
    private UUID templateId;

    @Column(name = "tag_id", nullable = false, updatable = false)
    private UUID tagId;

    @Column(name = "task_date", nullable = false, updatable = false)
    private LocalDate taskDate;

    @Column(nullable = false, length = 160, updatable = false)
    private String title;

    @Column(nullable = false, length = 1000, updatable = false)
    private String instruction;

    @Column(name = "tag_name", nullable = false, length = 80, updatable = false)
    private String tagName;

    @Column(name = "required_photo_count", nullable = false, updatable = false)
    private int requiredPhotoCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "schedule_type", nullable = false, length = 16, updatable = false)
    private TaskScheduleType scheduleType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TaskStatus status = TaskStatus.PENDING;

    @Column(name = "submitted_by_user_id")
    private UUID submittedByUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "submitted_by_role", length = 32)
    private SystemRole submittedByRole;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column
    private Integer rating;

    @Column(name = "rating_comment", length = 1000)
    private String ratingComment;

    @Column(name = "rated_by_user_id")
    private UUID ratedByUserId;

    @Column(name = "rated_at")
    private Instant ratedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TaskRecord() {
    }

    public TaskRecord(
            TaskTemplate template,
            LocalDate taskDate,
            String tagName
    ) {
        this.tenantId = Objects.requireNonNull(template.getTenantId());
        this.templateId = Objects.requireNonNull(template.getId());
        this.tagId = Objects.requireNonNull(template.getTagId());
        this.taskDate = Objects.requireNonNull(taskDate, "taskDate must not be null");
        this.title = template.getTitle();
        this.instruction = template.getInstruction();
        this.tagName = requireText(tagName, "tagName");
        this.requiredPhotoCount = template.getRequiredPhotoCount();
        this.scheduleType = template.getScheduleType();
    }

    public void submit(UUID userId, SystemRole role, Instant when) {
        if (status != TaskStatus.PENDING) {
            throw new IllegalStateException("Only a pending task may be submitted.");
        }
        submittedByUserId = Objects.requireNonNull(userId, "userId must not be null");
        submittedByRole = Objects.requireNonNull(role, "role must not be null");
        submittedAt = Objects.requireNonNull(when, "when must not be null");
        status = TaskStatus.SUBMITTED;
    }

    public void rate(int stars, String comment, UUID userId, Instant when) {
        if (status != TaskStatus.SUBMITTED) {
            throw new IllegalStateException("Only a submitted task may be rated.");
        }
        if (stars < 1 || stars > 5) throw new IllegalArgumentException("rating must be between 1 and 5");
        rating = stars;
        ratingComment = requireText(comment, "comment");
        ratedByUserId = Objects.requireNonNull(userId, "userId must not be null");
        ratedAt = Objects.requireNonNull(when, "when must not be null");
        status = TaskStatus.DONE;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getTemplateId() { return templateId; }
    public UUID getTagId() { return tagId; }
    public LocalDate getTaskDate() { return taskDate; }
    public String getTitle() { return title; }
    public String getInstruction() { return instruction; }
    public String getTagName() { return tagName; }
    public int getRequiredPhotoCount() { return requiredPhotoCount; }
    public TaskScheduleType getScheduleType() { return scheduleType; }
    public TaskStatus getStatus() { return status; }
    public UUID getSubmittedByUserId() { return submittedByUserId; }
    public SystemRole getSubmittedByRole() { return submittedByRole; }
    public Instant getSubmittedAt() { return submittedAt; }
    public Integer getRating() { return rating; }
    public String getRatingComment() { return ratingComment; }
    public UUID getRatedByUserId() { return ratedByUserId; }
    public Instant getRatedAt() { return ratedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    private static String requireText(String value, String field) {
        String normalised = Objects.requireNonNull(value, field + " must not be null").trim();
        if (normalised.isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
        return normalised;
    }
}
