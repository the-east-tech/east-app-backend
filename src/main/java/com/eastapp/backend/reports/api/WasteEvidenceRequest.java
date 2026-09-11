package com.eastapp.backend.reports.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WasteEvidenceRequest(
        @NotBlank @Size(max = 500) String reason,
        @NotBlank @Size(max = 80) String photoStorageKey
) {
}
