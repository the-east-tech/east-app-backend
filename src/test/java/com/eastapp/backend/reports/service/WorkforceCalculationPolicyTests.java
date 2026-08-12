package com.eastapp.backend.reports.service;

import com.eastapp.backend.attendance.AttendanceEventType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorkforceCalculationPolicyTests {
    private static final ZoneId ZONE = ZoneId.of("Asia/Kuala_Lumpur");
    private static final LocalDate DAY = LocalDate.of(2026, 8, 4);
    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void ignoresCheckInWithoutCheckoutFromHoursAndWorkingStaff() {
        WorkforceCalculationPolicy.Result result = calculate(
                List.of(event(AttendanceEventType.CLOCK_IN, "2026-08-04T01:00:00Z")),
                List.of(new WorkforceCalculationPolicy.ReportedStaff(DAY, 0))
        );

        assertEquals(0, result.totalSeconds());
        assertEquals(0, result.completedShiftCount());
        assertEquals(0, result.staffDayCount());
        assertEquals(1, result.openShiftCount());
        assertEquals(0, result.staffCountMismatchDays());
    }

    @Test
    void countsOnlyACompletedCheckInCheckoutPair() {
        WorkforceCalculationPolicy.Result result = calculate(
                List.of(
                        event(AttendanceEventType.CLOCK_IN, "2026-08-04T01:00:00Z"),
                        event(AttendanceEventType.CLOCK_OUT, "2026-08-04T09:30:00Z")
                ),
                List.of(new WorkforceCalculationPolicy.ReportedStaff(DAY, 1))
        );

        assertEquals(30_600, result.totalSeconds());
        assertEquals(1, result.completedShiftCount());
        assertEquals(1, result.staffDayCount());
        assertEquals(0, result.openShiftCount());
        assertEquals(0, result.staffCountMismatchDays());
    }

    @Test
    void anIncompleteShiftDoesNotHideAttendanceMismatch() {
        WorkforceCalculationPolicy.Result result = calculate(
                List.of(event(AttendanceEventType.CLOCK_IN, "2026-08-04T01:00:00Z")),
                List.of(new WorkforceCalculationPolicy.ReportedStaff(DAY, 1))
        );

        assertEquals(0, result.staffDayCount());
        assertEquals(1, result.staffCountMismatchDays());
    }

    @Test
    void aSecondCheckInLeavesTheFirstShiftOpenButCountsTheCompletedSecondShift() {
        WorkforceCalculationPolicy.Result result = calculate(
                List.of(
                        event(AttendanceEventType.CLOCK_IN, "2026-08-04T01:00:00Z"),
                        event(AttendanceEventType.CLOCK_IN, "2026-08-04T02:00:00Z"),
                        event(AttendanceEventType.CLOCK_OUT, "2026-08-04T04:00:00Z")
                ),
                List.of(new WorkforceCalculationPolicy.ReportedStaff(DAY, 1))
        );

        assertEquals(7_200, result.totalSeconds());
        assertEquals(1, result.completedShiftCount());
        assertEquals(1, result.openShiftCount());
        assertEquals(1, result.staffDayCount());
    }

    private WorkforceCalculationPolicy.Result calculate(
            List<WorkforceCalculationPolicy.Event> events,
            List<WorkforceCalculationPolicy.ReportedStaff> reports
    ) {
        return WorkforceCalculationPolicy.calculate(events, reports, DAY, DAY, ZONE);
    }

    private WorkforceCalculationPolicy.Event event(
            AttendanceEventType type,
            String instant
    ) {
        return new WorkforceCalculationPolicy.Event(USER, type, Instant.parse(instant));
    }
}
