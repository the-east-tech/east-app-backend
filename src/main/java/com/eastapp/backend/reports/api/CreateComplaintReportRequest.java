package com.eastapp.backend.reports.api;

import com.eastapp.backend.reports.ComplaintStatus;
import com.eastapp.backend.reports.CustomerGender;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateComplaintReportRequest(
        @NotNull LocalDate reportDate,
        @NotBlank @Size(max = 80) String photoStorageKey,
        @NotNull CustomerGender customerGender,
        @Min(1) @Max(120) int estimatedAge,
        @NotBlank @Size(max = 1500) String complaintInfo,
        @Size(max = 32) String phoneE164,
        @NotBlank @Size(max = 1500) String actionTaken,
        @DecimalMin("0.00") BigDecimal compensationAmountRm,
        @NotNull ComplaintStatus status
) {
}
