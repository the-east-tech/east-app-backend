package com.eastapp.backend.storage.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record StorageOverviewResponse(
        Instant measuredAt,
        long databaseBytes,
        long applicationTablesBytes,
        int maxViewRows,
        List<TableUsage> tables
) {
    public record TableUsage(
            String tableName,
            String group,
            String dataUse,
            long rowCount,
            LocalDate oldestDate,
            LocalDate latestDate,
            long dataBytes,
            long indexBytes,
            long totalBytes,
            boolean deleteAllowed,
            long deletableRows,
            String deleteDescription
    ) {
    }
}
