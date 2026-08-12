package com.eastapp.backend.reports.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UpsertSalesReportRequest(
        @NotNull LocalDate reportDate,
        @NotNull @DecimalMin("0.00") @Digits(integer = 12, fraction = 2) BigDecimal cashTotalRm,
        @NotBlank @Size(max = 120) String cashReceivedBy,
        @NotNull @DecimalMin("0.00") @Digits(integer = 12, fraction = 2) BigDecimal foodDeliverySalesRm,
        @NotNull @DecimalMin("0.00") @Digits(integer = 12, fraction = 2) BigDecimal ewalletTotalRm,
        @Min(1) @Max(500) int staffOnDuty
) {
}
