package com.eastapp.backend.reports.api;

import java.math.BigDecimal;
import java.util.UUID;

public record InventoryRiskResponse(
        UUID skuId,
        String skuName,
        String severity,
        BigDecimal currentBalance,
        BigDecimal minimumBalance,
        BigDecimal maximumBalance,
        BigDecimal estimatedValueAtRiskRm,
        String insight
) {
}
