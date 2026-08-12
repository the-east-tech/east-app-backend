package com.eastapp.backend.reports.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AddVoidBillRequest(
        @NotNull LocalDate reportDate,
        @NotBlank @Size(max = 80) String billNumber,
        @NotBlank @Size(max = 500) String reason,
        @NotNull @DecimalMin(value = "0.01") BigDecimal amountRm,
        @NotBlank @Size(max = 80) String photoStorageKey
) {
}
