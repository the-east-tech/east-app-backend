package com.eastapp.backend.tasks.api;

import com.eastapp.backend.tasks.TaskStatus;
import com.eastapp.backend.tasks.TaskScheduleType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record TaskRecordResponse(
        UUID id,
        UUID templateId,
        UUID tagId,
        String tagName,
        LocalDate taskDate,
        String title,
        String instruction,
        UUID linkedSopId,
        String linkedSopTitle,
        int requiredPhotoCount,
        TaskScheduleType scheduleType,
        int photoCount,
        TaskStatus status,
        List<TaskChecklistItemResponse> checklistItems,
        List<TaskPhotoResponse> photos,
        boolean requirementsMet,
        TaskPersonResponse submittedBy,
        Instant submittedAt,
        Integer rating,
        String ratingComment,
        TaskPersonResponse ratedBy,
        Instant ratedAt,
        boolean canContribute,
        boolean canSubmit,
        boolean canRate,
        List<TaskAuditResponse> activity
) {
}
