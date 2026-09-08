package com.eastapp.backend.stock.api;

import com.eastapp.backend.stock.StockSkuChangeType;
import com.eastapp.backend.stock.StockWorkflowStatus;

import java.time.Instant;
import java.util.UUID;

public record StockSkuChangeRequestResponse(
        UUID id,
        UUID skuId,
        String skuName,
        StockSkuChangeType changeType,
        StockWorkflowStatus workflowStatus,
        UpsertStockSkuRequest proposedData,
        UUID requestedByUserId,
        String requestedByName,
        Instant submittedAt,
        UUID reviewedByUserId,
        String reviewedByName,
        Instant reviewedAt,
        String reviewNote
) {
}
