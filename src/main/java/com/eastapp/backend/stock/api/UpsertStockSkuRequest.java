package com.eastapp.backend.stock.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record UpsertStockSkuRequest(
        @NotBlank @Size(max = 120) String name,
        UUID tag1Id,
        UUID tag2Id,
        @NotBlank @Size(max = 32) String unit,
        @NotNull @DecimalMin("0") BigDecimal minimumBalanceValue,
        @NotNull @DecimalMin("0") BigDecimal maximumBalanceValue,
        @NotNull @DecimalMin("0") BigDecimal currentBalanceValue,
        @Min(1) @Max(100) int recoveryPercent,
        @NotNull @DecimalMin("0") BigDecimal minimumPriceRm,
        @NotNull @DecimalMin("0") BigDecimal maximumPriceRm,
        List<UUID> supplierIds,
        @Size(max = 500) String photoPath,
        List<@Size(max = 120) String> assignedStaffNames,
        List<@Size(max = 300) String> receivingChecklist,
        @Min(1) int stockCheckFrequencyDays,
        @NotBlank String resetTime,
        boolean active,
        boolean coolingPeriod
) {}
