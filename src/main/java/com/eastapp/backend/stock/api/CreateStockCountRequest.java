package com.eastapp.backend.stock.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record CreateStockCountRequest(
        @NotNull UUID skuId,
        @NotNull Instant capturedAt,
        @NotBlank @Size(max = 500) String stockPhotoName,
        @NotBlank @Size(max = 500) String invoicePhotoName,
        @NotNull @DecimalMin("0") BigDecimal currentBalanceValue,
        Map<String, Boolean> checkedItems,
        Map<String, String> remarks
) {}
