package com.eastapp.backend.stock.api;

import com.eastapp.backend.stock.StockSkuCsvOperation;
import com.eastapp.backend.stock.StockSkuCsvRequestStatus;

import java.time.Instant;
import java.util.UUID;

public record StockSkuCsvRequestResponse(
        UUID id,
        StockSkuCsvOperation operation,
        StockSkuCsvRequestStatus status,
        String fileName,
        int totalRows,
        int readyRows,
        int duplicateRows,
        int newTagCount,
        int unmatchedSupplierCount,
        UUID requestedByUserId,
        String requestedByName,
        Instant submittedAt,
        UUID reviewedByUserId,
        String reviewedByName,
        Instant reviewedAt,
        String reviewNote
) {}
