package com.eastapp.backend.stock.api;

public record StockSkuCsvImportResponse(
        int importedRows,
        int skippedDuplicateRows,
        int createdTags,
        int unmatchedSupplierLinks
) {}
