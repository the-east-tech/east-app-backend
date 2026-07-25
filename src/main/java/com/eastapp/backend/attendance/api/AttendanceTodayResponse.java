package com.eastapp.backend.attendance.api;

import java.time.LocalDate;

public record AttendanceTodayResponse(
        LocalDate date,
        String status,
        AttendanceEventResponse clockIn,
        AttendanceEventResponse clockOut,
        long totalWorkingMinutes
) {
}
