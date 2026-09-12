package com.eastapp.backend.storage.api;

import java.time.Instant;
import java.util.UUID;

public record BusinessCleanupCompleteResponse(
        UUID runId,
        long deletedRecordCount,
        long deletedPhotoCount,
        Instant completedAt
) {
}
