package com.eastapp.backend.attendance.api;

public record AttendanceMonthSummaryResponse(
        int month,
        String label,
        int presentDays,
        int completedDays,
        int missingCheckOutDays,
        long totalWorkingMinutes,
        Long averageWorkingMinutes,
        double completionPercent
) {
}
