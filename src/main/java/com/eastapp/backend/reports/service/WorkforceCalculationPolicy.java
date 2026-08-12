package com.eastapp.backend.reports.service;

import com.eastapp.backend.attendance.AttendanceEventType;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class WorkforceCalculationPolicy {
    private WorkforceCalculationPolicy() {
    }

    static Result calculate(
            List<Event> events,
            List<ReportedStaff> reportedStaff,
            LocalDate from,
            LocalDate to,
            ZoneId zoneId
    ) {
        Map<UUID, List<Event>> eventsByUser = new HashMap<>();
        for (Event event : events) {
            eventsByUser.computeIfAbsent(event.userId(), ignored -> new ArrayList<>())
                    .add(event);
        }

        long totalSeconds = 0;
        int completedShifts = 0;
        int openShifts = 0;
        Set<UserDay> staffDays = new HashSet<>();
        Map<LocalDate, Set<UUID>> staffByDay = new HashMap<>();

        for (Map.Entry<UUID, List<Event>> entry : eventsByUser.entrySet()) {
            UUID userId = entry.getKey();
            List<Event> userEvents = entry.getValue().stream()
                    .sorted(Comparator.comparing(Event::occurredAt))
                    .toList();
            Event open = null;

            for (Event event : userEvents) {
                if (event.eventType() == AttendanceEventType.CLOCK_IN) {
                    if (open != null && isWithin(open.localDate(zoneId), from, to)) {
                        openShifts++;
                    }
                    open = event;
                    continue;
                }

                if (open == null) continue;
                LocalDate workDate = open.localDate(zoneId);
                if (isWithin(workDate, from, to)) {
                    totalSeconds += Math.max(
                            0,
                            Duration.between(open.occurredAt(), event.occurredAt()).getSeconds()
                    );
                    completedShifts++;
                    staffDays.add(new UserDay(userId, workDate));
                    staffByDay.computeIfAbsent(workDate, ignored -> new HashSet<>())
                            .add(userId);
                }
                open = null;
            }

            if (open != null && isWithin(open.localDate(zoneId), from, to)) {
                openShifts++;
            }
        }

        Set<LocalDate> operatingDates = new HashSet<>(staffByDay.keySet());
        reportedStaff.stream()
                .map(ReportedStaff::reportDate)
                .forEach(operatingDates::add);

        int mismatchDays = 0;
        for (ReportedStaff report : reportedStaff) {
            int attendanceCount = staffByDay
                    .getOrDefault(report.reportDate(), Set.of())
                    .size();
            if (attendanceCount != report.staffCount()) mismatchDays++;
        }

        return new Result(
                totalSeconds,
                completedShifts,
                openShifts,
                staffDays.size(),
                operatingDates.size(),
                mismatchDays
        );
    }

    private static boolean isWithin(LocalDate date, LocalDate from, LocalDate to) {
        return !date.isBefore(from) && !date.isAfter(to);
    }

    record Event(UUID userId, AttendanceEventType eventType, Instant occurredAt) {
        LocalDate localDate(ZoneId zoneId) {
            return occurredAt.atZone(zoneId).toLocalDate();
        }
    }

    record ReportedStaff(LocalDate reportDate, int staffCount) {
    }

    record Result(
            long totalSeconds,
            int completedShiftCount,
            int openShiftCount,
            int staffDayCount,
            int operatingDayCount,
            int staffCountMismatchDays
    ) {
    }

    private record UserDay(UUID userId, LocalDate date) {
    }
}
