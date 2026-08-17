package com.eastapp.backend.reports.api;

import java.math.BigDecimal;

public record SalesOverviewResponse(
        BigDecimal grossSalesRm,
        BigDecimal netSalesRm,
        BigDecimal grossFoodDeliverySalesRm,
        BigDecimal netFoodDeliverySalesRm,
        BigDecimal estimatedPlatformCommissionRm,
        BigDecimal voidTotalRm,
        BigDecimal salesPerStaffRm,
        BigDecimal averageDailySalesRm,
        BigDecimal voidRatePercent,
        BigDecimal versusPreviousPeriodPercent,
        BigDecimal averageStaffPerReportedDay,
        int reportedDayCount,
        boolean submittedToday
) {
}
