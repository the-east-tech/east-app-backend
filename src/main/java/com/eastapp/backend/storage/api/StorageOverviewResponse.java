package com.eastapp.backend.storage.api;

import java.time.Instant;
import java.util.List;

public record StorageOverviewResponse(
        Instant measuredAt,
        long databaseBytes,
        long applicationTablesBytes,
        List<TableUsage> tables,
        List<CleanupAction> cleanupActions
) {
    public record TableUsage(
            String tableName,
            String group,
            String dataUse,
            long estimatedRows,
            long dataBytes,
            long indexBytes,
            long totalBytes
    ) {
    }

    public record CleanupAction(
            String key,
            String title,
            String description,
            int retentionDays,
            long currentBytes,
            boolean automatic
    ) {
    }
}
