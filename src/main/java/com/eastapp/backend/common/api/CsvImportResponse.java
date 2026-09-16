package com.eastapp.backend.common.api;

public record CsvImportResponse(
        int importedRows,
        int skippedDuplicateRows
) {}
