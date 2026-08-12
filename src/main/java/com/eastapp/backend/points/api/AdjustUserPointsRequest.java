package com.eastapp.backend.points.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record AdjustUserPointsRequest(
        @NotNull UUID userId,
        @Min(-10) @Max(10) int pointsDelta,
        @NotBlank @Size(max = 300) String reason
) {}
