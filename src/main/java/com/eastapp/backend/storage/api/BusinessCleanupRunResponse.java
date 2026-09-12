package com.eastapp.backend.storage.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

public record BusinessCleanupRunResponse(
        UUID id,
        LocalDate cutoffDate,
        Set<BusinessCleanupDataType> dataTypes,
        BusinessCleanupMediaMode mediaMode,
        Set<BusinessCleanupMediaType> mediaTypes,
        long recordCount,
        long blockedRecordCount,
        long photoCount,
        long excludedPhotoCount,
        long zipSizeBytes,
        String zipFileName,
        String zipSha256,
        String status,
        Instant createdAt,
        Instant savedConfirmedAt,
        Instant completedAt,
        long deletedRecordCount,
        long deletedPhotoCount
) {
}
