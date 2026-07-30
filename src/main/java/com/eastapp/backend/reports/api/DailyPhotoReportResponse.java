package com.eastapp.backend.reports.api;

import com.eastapp.backend.reports.ReportWorkflowStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record DailyPhotoReportResponse(
        UUID id,
        LocalDate reportDate,
        ReportWorkflowStatus workflowStatus,
        UUID userId,
        String userName,
        int photoCount,
        int minimumRequired,
        boolean requirementMet,
        Instant submittedAt,
        String reviewedByName,
        String reviewNote,
        List<DailyPhotoItemResponse> photos
) {
}
