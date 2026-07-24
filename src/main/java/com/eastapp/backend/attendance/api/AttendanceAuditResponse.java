package com.eastapp.backend.attendance.api;

import com.eastapp.backend.attendance.AttendanceReportPeriod;

import java.time.LocalDate;
import java.util.List;

public record AttendanceAuditResponse(
        AttendanceReportPeriod period,
        LocalDate startDate,
        LocalDate endDate,
        String label,
        AttendanceAuditSummaryResponse summary,
        List<AttendanceUserAuditResponse> users
) {
}
