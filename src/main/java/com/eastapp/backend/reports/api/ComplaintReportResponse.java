package com.eastapp.backend.reports.api;

import com.eastapp.backend.reports.ComplaintStatus;
import com.eastapp.backend.reports.CustomerGender;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ComplaintReportResponse(
        UUID id,
        LocalDate reportDate,
        ComplaintStatus status,
        String photoStorageKey,
        CustomerGender customerGender,
        int estimatedAge,
        String complaintInfo,
        String phoneE164,
        String actionTaken,
        BigDecimal compensationAmountRm,
        String submittedByName,
        Instant submittedAt,
        Instant resolvedAt
) {
}
