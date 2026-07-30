package com.eastapp.backend.reports.api;

import com.eastapp.backend.reports.ComplaintStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record UpdateComplaintRequest(
        @NotNull ComplaintStatus status,
        @NotBlank @Size(max = 1500) String actionTaken,
        @DecimalMin("0.00") BigDecimal compensationAmountRm
) {
}
