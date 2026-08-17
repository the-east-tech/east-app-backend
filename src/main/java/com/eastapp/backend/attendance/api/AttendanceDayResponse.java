package com.eastapp.backend.attendance.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record AttendanceDayResponse(
        LocalDate date,
        String status,
        Instant checkInAt,
        Instant checkOutAt,
        long workingMinutes,
        List<AttendanceEventResponse> events
) {
}
