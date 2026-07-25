package com.eastapp.backend.stock.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateStockReceivingItemRequest(
        @NotNull UUID skuId,
        @NotNull @DecimalMin("0") BigDecimal invoiceQuantity,
        @NotNull @DecimalMin("0") BigDecimal receivedQuantity,
        @Size(max = 80) String condition,
        @Size(max = 1000) String note
) {}
