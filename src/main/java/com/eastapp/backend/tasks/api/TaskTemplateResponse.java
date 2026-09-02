package com.eastapp.backend.tasks.api;

import com.eastapp.backend.tasks.TaskScheduleType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record TaskTemplateResponse(
        UUID id,
        UUID tagId,
        String tagName,
        String title,
        String instruction,
        UUID linkedSopId,
        String linkedSopTitle,
        int requiredPhotoCount,
        TaskScheduleType scheduleType,
        LocalDate firstTaskDate,
        LocalDate endDate,
        List<String> checklistItems,
        boolean active,
        TaskPersonResponse createdBy,
        TaskPersonResponse updatedBy,
        Instant createdAt,
        Instant updatedAt,
        List<TaskAuditResponse> activity
) {
}
