package com.eastapp.backend.tasks.api;

import java.time.Instant;
import java.util.UUID;

public record DailyTaskAuditResponse(
        UUID id,
        String action,
        String details,
        DailyTaskPersonResponse actor,
        Instant occurredAt
) {
}
