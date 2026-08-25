package com.eastapp.backend.tasks.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record UpsertDailyTaskTemplateRequest(
        @NotBlank @Size(max = 160) String title,
        @Size(max = 1000) String instruction,
        @NotNull UUID tagId,
        @Min(1) @Max(40) int requiredPhotoCount,
        @NotNull @Size(min = 1, max = 5)
        List<@NotBlank @Size(max = 300) String> checklistItems,
        boolean active
) {
    public UpsertDailyTaskTemplateRequest {
        checklistItems = checklistItems == null ? null : List.copyOf(checklistItems);
    }
}
