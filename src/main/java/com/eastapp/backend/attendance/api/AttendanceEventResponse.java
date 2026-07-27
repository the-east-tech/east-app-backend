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
        String capturedAddress,
        String workLocationName,
        String workLocationAddress,
        double workLocationLatitude,
        double workLocationLongitude,
        double distanceMeters,
        boolean cameraCaptureValid,
        boolean faceValid,
        int faceCount,
        boolean qrCheckpointValid,
        String devicePlatform,
        String appVersion,
        String validationMethod,
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
                event.getCapturedAddress(),
                event.getWorkLocationName(),
                event.getWorkLocationAddress(),
                event.getWorkLocationLatitude(),
                event.getWorkLocationLongitude(),
                event.getDistanceMeters(),
                event.isCameraCaptureValid(),
                event.isFaceValid(),
                event.getFaceCount(),
                event.isQrCheckpointValid(),
                event.getDevicePlatform(),
                event.getAppVersion(),
                event.getValidationMethod(),
                false
        );
    }
}
