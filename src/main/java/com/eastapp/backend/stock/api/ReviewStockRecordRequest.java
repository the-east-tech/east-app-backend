package com.eastapp.backend.stock.api;

import com.eastapp.backend.stock.StockWorkflowStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReviewStockRecordRequest(
        @NotNull StockWorkflowStatus status,
        @Size(max = 1000) String note
) {}
