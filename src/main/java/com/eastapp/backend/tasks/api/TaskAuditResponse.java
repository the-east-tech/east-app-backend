package com.eastapp.backend.tasks.api;

import java.time.Instant;
import java.util.UUID;

public record TaskAuditResponse(
        UUID id,
        String action,
        String details,
        TaskPersonResponse actor,
        Instant occurredAt
) {
}
