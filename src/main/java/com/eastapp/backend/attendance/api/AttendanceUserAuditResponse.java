package com.eastapp.backend.attendance.api;

import java.time.Instant;
import java.util.UUID;

public record AttendanceUserAuditResponse(
        UUID userId,
        String employeeId,
        String fullName,
        String roleName,
        boolean active,
        String status,
        int presentDays,
        int completedDays,
        int missingCheckOutDays,
        int outsideGeofenceEvents,
        int validEvents,
        Instant firstClockInAt,
        Instant lastClockOutAt,
        String averageClockInTime,
        Long averageWorkingMinutes,
        double completionPercent
) {
}
