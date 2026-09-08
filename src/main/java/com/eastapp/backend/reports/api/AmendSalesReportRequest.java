package com.eastapp.backend.reports.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AmendSalesReportRequest(
        @NotBlank @Size(max = 500) String reason
) {
}
