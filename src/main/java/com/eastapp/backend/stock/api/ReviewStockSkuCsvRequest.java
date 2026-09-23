package com.eastapp.backend.stock.api;

import com.eastapp.backend.stock.StockSkuCsvRequestStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReviewStockSkuCsvRequest(
        @NotNull StockSkuCsvRequestStatus status,
        @Size(max = 1000) String note
) {}
