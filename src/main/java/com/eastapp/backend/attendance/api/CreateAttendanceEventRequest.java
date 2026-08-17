package com.eastapp.backend.attendance.api;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record CreateAttendanceEventRequest(
        @NotBlank @Size(max = 64) String clientEventId,
        @NotNull Instant deviceCapturedAt,
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
        @NotNull @DecimalMin("0.0") Double accuracyMeters,
        @NotBlank @Size(max = 256) String qrPayload,
        @NotBlank @Size(max = 32) String devicePlatform,
        @Size(max = 160) String deviceOsVersion,
        @NotBlank @Size(max = 40) String appVersion
) {
}
