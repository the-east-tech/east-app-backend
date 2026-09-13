package com.eastapp.backend.support.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ErrorReportRequest(
        @NotBlank @Size(max = 80) String reference,
        @NotBlank @Size(max = 4_000) String errorDetails,
        @NotBlank @Size(max = 40_000) String debugReport
) {
}
