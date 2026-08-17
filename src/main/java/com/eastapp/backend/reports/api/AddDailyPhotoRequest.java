package com.eastapp.backend.reports.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record AddDailyPhotoRequest(
        @NotNull LocalDate reportDate,
        @NotBlank @Size(max = 80) String photoStorageKey
) {
}
