package com.eastapp.backend.reports.api;

import java.math.BigDecimal;

public record PeriodCountCoverageResponse(
        BigDecimal countCoveragePercent,
        long countedSkuDays,
        long expectedSkuDays,
        long missingCountSkuDays
) {
}
