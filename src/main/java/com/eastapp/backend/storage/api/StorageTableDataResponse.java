package com.eastapp.backend.storage.api;

import java.util.List;

public record StorageTableDataResponse(
        String tableName,
        int requestedRows,
        int returnedRows,
        List<Column> columns,
        List<List<String>> rows
) {
    public record Column(
            String name,
            String dataType,
            boolean nullable
    ) {
    }
}
