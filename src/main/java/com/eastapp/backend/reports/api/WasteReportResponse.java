package com.eastapp.backend.reports.api;

import com.eastapp.backend.reports.ReportWorkflowStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record WasteReportResponse(
        UUID id,
        LocalDate reportDate,
        ReportWorkflowStatus workflowStatus,
        UUID skuId,
        String itemName,
        BigDecimal quantity,
        String unit,
        BigDecimal estimatedUnitCostRm,
        BigDecimal estimatedLossRm,
        String reason,
        String photoStorageKey,
        String submittedByName,
        Instant submittedAt,
        String reviewedByName,
        String reviewNote
) {
}
