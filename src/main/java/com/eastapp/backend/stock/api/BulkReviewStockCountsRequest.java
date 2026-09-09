package com.eastapp.backend.stock.api;

import com.eastapp.backend.stock.StockWorkflowStatus;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record BulkReviewStockCountsRequest(
        @NotEmpty
        @Size(max = 100)
        List<@NotNull UUID> submissionIds,

        @NotNull StockWorkflowStatus status,

        @Size(max = 1000)
        String note
) {
}
