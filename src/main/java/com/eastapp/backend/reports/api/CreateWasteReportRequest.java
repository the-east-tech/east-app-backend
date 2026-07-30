package com.eastapp.backend.reports.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateWasteReportRequest(
        @NotNull LocalDate reportDate,
        UUID skuId,
        @NotBlank @Size(max = 160) String itemName,
        @NotNull @DecimalMin(value = "0.01") BigDecimal quantity,
        @NotBlank @Size(max = 32) String unit,
        @NotNull @DecimalMin("0.00") BigDecimal estimatedUnitCostRm,
        @NotBlank @Size(max = 500) String reason,
        @NotBlank @Size(max = 80) String photoStorageKey
) {
}
