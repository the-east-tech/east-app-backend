package com.eastapp.backend.tasks.api;

import java.time.Instant;
import java.util.UUID;

public record DailyTaskPhotoResponse(
        UUID id,
        String photoStorageKey,
        DailyTaskPersonResponse submittedBy,
        Instant submittedAt
) {
}
