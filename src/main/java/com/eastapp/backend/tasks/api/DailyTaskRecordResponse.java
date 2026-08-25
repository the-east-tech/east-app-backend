package com.eastapp.backend.tasks.api;

import com.eastapp.backend.tasks.DailyTaskStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record DailyTaskRecordResponse(
        UUID id,
        UUID templateId,
        UUID tagId,
        String tagName,
        LocalDate taskDate,
        String title,
        String instruction,
        int requiredPhotoCount,
        int photoCount,
        DailyTaskStatus status,
        List<DailyTaskChecklistItemResponse> checklistItems,
        List<DailyTaskPhotoResponse> photos,
        boolean requirementsMet,
        DailyTaskPersonResponse submittedBy,
        Instant submittedAt,
        Integer rating,
        String ratingComment,
        DailyTaskPersonResponse ratedBy,
        Instant ratedAt,
        boolean canContribute,
        boolean canSubmit,
        boolean canRate,
        List<DailyTaskAuditResponse> activity
) {
}
