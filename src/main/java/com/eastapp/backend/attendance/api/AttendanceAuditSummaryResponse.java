package com.eastapp.backend.attendance.api;

public record AttendanceAuditSummaryResponse(
        int people,
        int peopleWithAttendance,
        int presentDays,
        int completedDays,
        int missingCheckOutDays,
        int outsideGeofenceEvents,
        double completionPercent
) {
}
