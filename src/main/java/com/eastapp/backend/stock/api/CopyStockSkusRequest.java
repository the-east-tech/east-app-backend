package com.eastapp.backend.stock.api;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CopyStockSkusRequest(
        @NotNull
        UUID sourceTenantId,

        @NotEmpty
        @Size(max = 100)
        List<@NotNull UUID> skuIds
) {
}
