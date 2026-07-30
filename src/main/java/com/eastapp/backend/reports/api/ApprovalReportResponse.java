package com.eastapp.backend.reports.api;

import com.eastapp.backend.reports.BusinessReportType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ApprovalReportResponse(
        UUID id,
        BusinessReportType reportType,
        LocalDate reportDate,
        UUID submittedByUserId,
        String submittedByName,
        Instant submittedAt,
        String summary,
        BigDecimal amountRm,
        int evidenceCount
) {
}
