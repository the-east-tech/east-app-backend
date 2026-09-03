package com.eastapp.backend.stock.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MarkSupplierOrderedRequest(
        @NotBlank @Size(max = 4000) String message
) {}
