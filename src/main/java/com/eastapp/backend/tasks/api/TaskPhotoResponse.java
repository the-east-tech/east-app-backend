package com.eastapp.backend.tasks.api;

import java.time.Instant;
import java.util.UUID;

public record TaskPhotoResponse(
        UUID id,
        String photoStorageKey,
        TaskPersonResponse submittedBy,
        Instant submittedAt
) {
}
