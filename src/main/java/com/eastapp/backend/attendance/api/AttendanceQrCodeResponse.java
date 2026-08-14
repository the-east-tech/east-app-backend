package com.eastapp.backend.attendance.api;

import com.eastapp.backend.attendance.AttendanceEventType;

import java.time.Instant;
import java.util.UUID;

public record AttendanceQrCodeResponse(
        UUID id,
        AttendanceEventType eventType,
        Instant expiresAt,
        String qrPayload
) {
}
