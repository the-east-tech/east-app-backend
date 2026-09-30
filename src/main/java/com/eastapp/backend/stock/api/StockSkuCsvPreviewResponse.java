package com.eastapp.backend.stock.api;

import java.util.List;

public record StockSkuCsvPreviewResponse(
        int totalRows,
        int readyRows,
        int duplicateRows,
        int invalidRows,
        int newTagCount,
        int unmatchedSupplierCount,
        List<String> unmatchedSupplierNames,
        List<String> errors
) {}
