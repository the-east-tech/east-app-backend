package com.eastapp.backend.reports.api;

import java.math.BigDecimal;
import java.util.List;

public record InventoryIntelligenceResponse(
        int activeSkuCount,
        int healthySkuCount,
        int lowStockCount,
        int outOfStockCount,
        int overstockCount,
        BigDecimal estimatedStockValueRm,
        BigDecimal estimatedReorderInvestmentRm,
        BigDecimal estimatedOverstockCapitalRm,
        BigDecimal healthScorePercent,
        List<InventoryRiskResponse> topRisks
) {
}
