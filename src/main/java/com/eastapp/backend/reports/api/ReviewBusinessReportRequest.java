package com.eastapp.backend.reports.api;

import com.eastapp.backend.reports.ReportWorkflowStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReviewBusinessReportRequest(
        @NotNull ReportWorkflowStatus status,
        @Size(max = 500) String note
) {
}
