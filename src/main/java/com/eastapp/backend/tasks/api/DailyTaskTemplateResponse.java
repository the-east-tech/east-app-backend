package com.eastapp.backend.tasks.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DailyTaskTemplateResponse(
        UUID id,
        UUID tagId,
        String tagName,
        String title,
        String instruction,
        int requiredPhotoCount,
        List<String> checklistItems,
        boolean active,
        DailyTaskPersonResponse createdBy,
        DailyTaskPersonResponse updatedBy,
        Instant createdAt,
        Instant updatedAt,
        List<DailyTaskAuditResponse> activity
) {
}
