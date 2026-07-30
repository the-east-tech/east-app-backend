package com.eastapp.backend.attendance.api;

import com.eastapp.backend.attendance.AttendanceEventType;
import com.eastapp.backend.attendance.AttendanceFaceAttempt;

import java.time.Instant;
import java.util.UUID;

public record AttendanceFaceAttemptResponse(
        UUID id,
        AttendanceEventType intendedEventType,
        Instant recordedAt,
        Instant deviceAttemptedAt,
        double latitude,
        double longitude,
        double accuracyMeters,
        String capturedAddress,
        String workLocationName,
        String workLocationAddress,
        double workLocationLatitude,
        double workLocationLongitude,
        double distanceMeters,
        String failureReason,
        int faceCount,
        int faceAttemptNumber,
        Double faceBoxWidth,
        Double faceBoxHeight,
        Double faceYaw,
        Double faceRoll,
        Double facePitch,
        String devicePlatform,
        String appVersion,
        String validationMethod,
        boolean photoStored
) {
    public static AttendanceFaceAttemptResponse from(AttendanceFaceAttempt attempt) {
        return new AttendanceFaceAttemptResponse(
                attempt.getId(),
                attempt.getIntendedEventType(),
                attempt.getRecordedAt(),
                attempt.getDeviceAttemptedAt(),
                attempt.getLatitude(),
                attempt.getLongitude(),
                attempt.getAccuracyMeters(),
                attempt.getCapturedAddress(),
                attempt.getWorkLocationName(),
                attempt.getWorkLocationAddress(),
                attempt.getWorkLocationLatitude(),
                attempt.getWorkLocationLongitude(),
                attempt.getDistanceMeters(),
                attempt.getFailureReason(),
                attempt.getFaceCount(),
                attempt.getFaceAttemptNumber(),
                attempt.getFaceBoxWidth(),
                attempt.getFaceBoxHeight(),
                attempt.getFaceYaw(),
                attempt.getFaceRoll(),
                attempt.getFacePitch(),
                attempt.getDevicePlatform(),
                attempt.getAppVersion(),
                attempt.getValidationMethod(),
                attempt.hasPhoto()
        );
    }
}
