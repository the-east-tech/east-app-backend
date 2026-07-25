package com.eastapp.backend.stock.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateStockSupplierRequest(
        @NotBlank @Size(max = 120) String supplierName,
        @NotBlank @Size(max = 160) String supplierItem,
        @Size(max = 120) String contactPerson,
        @Size(max = 32) String phone,
        @Size(max = 500) String address,
        @Size(max = 1000) String notes,
        @NotBlank @Size(max = 32) String unit,
        @NotNull @DecimalMin("0") BigDecimal recommendedPurchaseAmount,
        @Size(max = 80) String recommendedPurchaseFrequency,
        @NotNull @DecimalMin("0") BigDecimal pricingPerUnit,
        @NotNull @DecimalMin("0") BigDecimal minimumBalanceValue,
        @NotNull @DecimalMin("0") BigDecimal maximumBalanceValue,
        @NotNull @DecimalMin("0") BigDecimal currentBalanceValue
) {}
