package com.eastapp.backend.attendance.api;

import com.eastapp.backend.attendance.AttendanceEvent;
import com.eastapp.backend.attendance.AttendanceEventType;

import java.time.Instant;
import java.util.UUID;

public record AttendanceEventResponse(
        UUID id,
        AttendanceEventType eventType,
        Instant occurredAt,
        Instant deviceCapturedAt,
        double latitude,
        double longitude,
        double accuracyMeters,
        String workLocationName,
        double distanceMeters,
        int allowedRadiusMeters,
        boolean withinGeofence,
        boolean cameraCaptureValid,
        boolean faceValid,
        int faceCount,
        boolean qrCheckpointValid,
        String devicePlatform,
        String appVersion,
        String validationMethod,
        boolean requiresReview,
        boolean photoStored
) {
    public static AttendanceEventResponse from(AttendanceEvent event) {
        return new AttendanceEventResponse(
                event.getId(),
                event.getEventType(),
                event.getOccurredAt(),
                event.getDeviceCapturedAt(),
                event.getLatitude(),
                event.getLongitude(),
                event.getAccuracyMeters(),
                event.getWorkLocationName(),
                event.getDistanceMeters(),
                event.getAllowedRadiusMeters(),
                event.isWithinGeofence(),
                event.isCameraCaptureValid(),
                event.isFaceValid(),
                event.getFaceCount(),
                event.isQrCheckpointValid(),
                event.getDevicePlatform(),
                event.getAppVersion(),
                event.getValidationMethod(),
                event.requiresReview(),
                false
        );
    }
}
