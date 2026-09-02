package com.eastapp.backend.reports.api;

import com.eastapp.backend.reports.ReportWorkflowStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record SalesReportResponse(
        UUID id,
        LocalDate reportDate,
        ReportWorkflowStatus workflowStatus,
        BigDecimal cashTotalRm,
        UUID cashReceivedByUserId,
        String cashReceivedBy,
        BigDecimal foodDeliverySalesRm,
        BigDecimal netFoodDeliverySalesRm,
        BigDecimal estimatedPlatformCommissionRm,
        BigDecimal ewalletTotalRm,
        BigDecimal totalSalesRm,
        BigDecimal voidTotalRm,
        int staffOnDuty,
        BigDecimal salesPerStaffRm,
        BigDecimal voidExposurePercent,
        String submittedByName,
        Instant submittedAt,
        String reviewedByName,
        String reviewNote,
        List<VoidBillResponse> voidBills
) {
}
