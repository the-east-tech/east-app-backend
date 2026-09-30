package com.eastapp.backend.stock.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CreateStockCountsRequest(
        @NotEmpty
        List<@NotNull @Valid CreateStockCountRequest> counts
) {
}
