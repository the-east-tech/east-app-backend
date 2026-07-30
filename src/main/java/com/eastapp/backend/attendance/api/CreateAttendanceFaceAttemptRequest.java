package com.eastapp.backend.attendance.api;

import com.eastapp.backend.attendance.AttendanceEventType;

import java.time.Instant;

public record CreateAttendanceFaceAttemptRequest(
        String clientAttemptId,
        AttendanceEventType intendedEventType,
        Instant deviceAttemptedAt,
        double latitude,
        double longitude,
        double accuracyMeters,
        String failureReason,
        int faceCount,
        int faceAttemptNumber,
        Double faceBoxWidth,
        Double faceBoxHeight,
        Double faceYaw,
        Double faceRoll,
        Double facePitch,
        String devicePlatform,
        String deviceOsVersion,
        String appVersion,
        String validationMethod
) {
}
