package com.eastapp.backend.storage.api;

import java.time.Instant;

public record StorageCleanupResponse(
        String key,
        int deletedRows,
        Instant completedAt
) {
}
