package com.eastapp.backend.reports.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record UpsertSalesReportRequest(
        @NotNull LocalDate reportDate,
        @NotNull @DecimalMin("0.00") @Digits(integer = 12, fraction = 2) BigDecimal cashTotalRm,
        @NotNull UUID cashReceivedByUserId,
        @NotNull @DecimalMin("0.00") @Digits(integer = 12, fraction = 2) BigDecimal foodDeliverySalesRm,
        @NotNull @DecimalMin("0.00") @Digits(integer = 12, fraction = 2) BigDecimal ewalletTotalRm,
        @Min(1) @Max(500) int staffOnDuty
) {
}
