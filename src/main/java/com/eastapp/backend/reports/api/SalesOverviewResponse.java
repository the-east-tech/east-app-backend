package com.eastapp.backend.reports.api;

import java.math.BigDecimal;

public record SalesOverviewResponse(
        BigDecimal grossSalesRm,
        BigDecimal netSalesRm,
        BigDecimal voidTotalRm,
        BigDecimal salesPerStaffRm,
        BigDecimal voidRatePercent,
        BigDecimal versusPreviousPeriodPercent,
        int staffCount,
        boolean submittedToday
) {
}
