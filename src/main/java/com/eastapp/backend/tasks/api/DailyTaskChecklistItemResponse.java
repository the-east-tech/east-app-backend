package com.eastapp.backend.tasks.api;

import java.time.Instant;
import java.util.UUID;

public record DailyTaskChecklistItemResponse(
        UUID id,
        int position,
        String description,
        boolean completed,
        DailyTaskPersonResponse completedBy,
        Instant completedAt
) {
}
