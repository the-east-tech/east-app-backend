package com.eastapp.backend.attendance.api;

import com.eastapp.backend.attendance.AttendanceReportPeriod;
import com.eastapp.backend.common.api.PageResponse;

import java.time.LocalDate;
import java.util.List;

public record AttendanceUserDetailResponse(
        AttendanceReportPeriod period,
        LocalDate startDate,
        LocalDate endDate,
        String label,
        AttendanceUserAuditResponse summary,
        List<AttendanceDayResponse> days,
        List<AttendanceMonthSummaryResponse> months,
        PageResponse<AttendanceEventResponse> events
) {
}
