package com.eastapp.backend.stock.api;

import java.util.List;

public record StockSupplierCsvPreviewResponse(
        String format,
        int formatVersion,
        int totalRows,
        int readyRows,
        int duplicateRows,
        int invalidRows,
        List<String> errors
) {}
