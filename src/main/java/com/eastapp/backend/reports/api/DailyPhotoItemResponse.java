package com.eastapp.backend.reports.api;

import java.time.Instant;
import java.util.UUID;

public record DailyPhotoItemResponse(
        UUID id,
        String photoStorageKey,
        Instant createdAt
) {
}
