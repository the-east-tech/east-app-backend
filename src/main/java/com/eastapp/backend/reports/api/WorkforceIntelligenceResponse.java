package com.eastapp.backend.reports.api;

import java.math.BigDecimal;

public record WorkforceIntelligenceResponse(
        BigDecimal totalLabourHours,
        BigDecimal salesPerLabourHourRm,
        BigDecimal averageStaffPerDay,
        BigDecimal averageHoursPerStaffDay,
        int completedShiftCount,
        int openShiftCount,
        int operatingDayCount,
        int staffCountMismatchDays
) {
}
