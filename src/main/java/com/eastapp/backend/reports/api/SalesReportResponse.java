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
        BigDecimal salesRm,
        BigDecimal subTotalRm,
        String cashReceivedBy,
        BigDecimal pandaSalesRm,
        BigDecimal grossSalesRm,
        BigDecimal voidTotalRm,
        BigDecimal netSalesRm,
        int staffCount,
        BigDecimal salesPerStaffRm,
        BigDecimal voidRatePercent,
        String submittedByName,
        Instant submittedAt,
        String reviewedByName,
        String reviewNote,
        List<VoidBillResponse> voidBills
) {
}
