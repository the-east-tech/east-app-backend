package com.eastapp.backend.tasks.api;

import java.time.Instant;
import java.util.UUID;

public record TaskChecklistItemResponse(
        UUID id,
        int position,
        String description,
        boolean completed,
        TaskPersonResponse completedBy,
        Instant completedAt
) {
}
