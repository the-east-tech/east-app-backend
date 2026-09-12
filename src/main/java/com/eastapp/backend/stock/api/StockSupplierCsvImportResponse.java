package com.eastapp.backend.stock.api;

public record StockSupplierCsvImportResponse(
        int importedRows,
        int skippedDuplicateRows
) {}
