package com.eastapp.backend.stock.api;

import java.util.List;

public record StockSupplierCsvPreviewResponse(
        int totalRows,
        int readyRows,
        int duplicateRows,
        int invalidRows,
        List<String> errors
) {}
