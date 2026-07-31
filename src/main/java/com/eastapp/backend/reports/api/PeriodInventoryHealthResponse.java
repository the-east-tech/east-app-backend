package com.eastapp.backend.reports.api;

import java.math.BigDecimal;

public record PeriodInventoryHealthResponse(
        BigDecimal healthPercent,
        BigDecimal countCoveragePercent,
        long healthySkuDays,
        long countedSkuDays,
        long expectedSkuDays,
        long lowStockOccurrences,
        long outOfStockOccurrences,
        long overstockOccurrences,
        long missingCountSkuDays
) {
}
