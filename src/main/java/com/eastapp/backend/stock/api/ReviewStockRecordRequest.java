package com.eastapp.backend.stock.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ReviewStockRecordRequest(
        @NotBlank @Pattern(regexp = "Approved|Rejected") String status,
        @Size(max = 1000) String note
) {}
