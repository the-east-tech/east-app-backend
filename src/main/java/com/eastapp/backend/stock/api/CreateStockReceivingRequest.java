package com.eastapp.backend.stock.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CreateStockReceivingRequest(
        @NotNull UUID supplierId,
        @NotNull Instant capturedAt,
        @NotBlank @Size(max = 500) String invoicePhotoName,
        @NotBlank @Size(max = 500) String goodsPhotoName,
        @NotEmpty List<@Valid CreateStockReceivingItemRequest> items
) {}
