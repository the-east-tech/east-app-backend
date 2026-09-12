package com.eastapp.backend.storage.api;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;

import java.time.LocalDate;
import java.util.Set;

public record BusinessCleanupRequest(
        @NotEmpty Set<BusinessCleanupDataType> dataTypes,
        @NotNull @PastOrPresent LocalDate cutoffDate,
        @NotNull BusinessCleanupMediaMode mediaMode,
        Set<BusinessCleanupMediaType> mediaTypes
) {
    public BusinessCleanupRequest {
        dataTypes = dataTypes == null ? Set.of() : Set.copyOf(dataTypes);
        mediaTypes = mediaTypes == null ? Set.of() : Set.copyOf(mediaTypes);
    }
}
