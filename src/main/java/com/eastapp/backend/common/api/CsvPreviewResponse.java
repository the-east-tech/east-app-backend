package com.eastapp.backend.common.api;

import java.util.List;

public record CsvPreviewResponse(
        String format,
        int formatVersion,
        int totalRows,
        int readyRows,
        int duplicateRows,
        int invalidRows,
        List<String> errors
) {}
