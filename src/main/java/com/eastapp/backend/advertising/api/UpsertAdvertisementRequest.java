package com.eastapp.backend.advertising.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record UpsertAdvertisementRequest(
        @NotBlank @Size(max = 80) String imageStorageKey,
        @NotNull Instant startsAt,
        @NotNull Instant endsAt,
        @Min(0) @Max(3) int displayOrder,
        boolean active
) {
}
