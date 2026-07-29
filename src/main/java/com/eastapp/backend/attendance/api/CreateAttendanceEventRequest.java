package com.eastapp.backend.attendance.api;

import com.eastapp.backend.attendance.AttendanceEventType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record CreateAttendanceEventRequest(
        @NotBlank
        @Size(max = 64)
        String clientEventId,

        @NotNull
        AttendanceEventType eventType,

        @NotNull
        Instant deviceCapturedAt,

        @NotNull
        @DecimalMin("-90.0")
        @DecimalMax("90.0")
        Double latitude,

        @NotNull
        @DecimalMin("-180.0")
        @DecimalMax("180.0")
        Double longitude,

        @NotNull
        @DecimalMin("0.0")
        Double accuracyMeters,

        @NotNull
        Boolean cameraCaptureValid,

        @NotNull
        Boolean faceValid,

        @NotNull
        @Min(0)
        @Max(10)
        Integer faceCount,

        @NotNull
        @Min(1)
        @Max(3)
        Integer faceAttemptCount,

        @NotNull
        Boolean faceVerificationBypassed,

        @DecimalMin("0.0")
        Double faceBoxWidth,

        @DecimalMin("0.0")
        Double faceBoxHeight,

        Double faceYaw,

        Double faceRoll,

        Double facePitch,

        @NotNull
        Boolean qrCheckpointValid,

        @NotBlank
        @Size(max = 32)
        String devicePlatform,

        @Size(max = 160)
        String deviceOsVersion,

        @NotBlank
        @Size(max = 40)
        String appVersion,

        @NotBlank
        @Size(max = 64)
        String validationMethod
) {
}
