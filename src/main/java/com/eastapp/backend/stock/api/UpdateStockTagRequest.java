package com.eastapp.backend.stock.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateStockTagRequest(
        @NotBlank @Size(max = 80) String tag
) {}
