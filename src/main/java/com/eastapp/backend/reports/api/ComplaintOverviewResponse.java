package com.eastapp.backend.reports.api;

import java.math.BigDecimal;

public record ComplaintOverviewResponse(
        long openCount,
        long resolvedInPeriod,
        double resolutionRatePercent,
        BigDecimal compensationInPeriodRm
) {
}
