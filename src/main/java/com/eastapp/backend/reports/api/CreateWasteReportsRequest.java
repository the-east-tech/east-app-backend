package com.eastapp.backend.reports.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record CreateWasteReportsRequest(
        @NotNull LocalDate reportDate,
        @NotEmpty @Size(max = 10) List<@Valid WasteEvidenceRequest> evidence
) {
}
