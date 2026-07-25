package com.eastapp.backend.attendance.api;

import com.eastapp.backend.attendance.AttendanceReportPeriod;
import com.eastapp.backend.attendance.service.AttendanceService;
import com.eastapp.backend.auth.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/attendance")
public class AttendanceController {

    private final AttendanceService attendanceService;

    public AttendanceController(AttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    @PostMapping("/events")
    ResponseEntity<AttendanceEventResponse> create(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody CreateAttendanceEventRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(attendanceService.create(principal, request));
    }

    @GetMapping("/today")
    AttendanceTodayResponse today(
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        return attendanceService.today(principal);
    }

    @GetMapping("/audit")
    @PreAuthorize("hasRole('HEAD')")
    AttendanceAuditResponse audit(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(defaultValue = "DAY") AttendanceReportPeriod period,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate anchor
    ) {
        return attendanceService.audit(principal, period, anchor);
    }
    @GetMapping("/users/{userId}")
    @PreAuthorize("hasRole('HEAD')")
    AttendanceUserDetailResponse userAudit(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable java.util.UUID userId,
            @RequestParam(defaultValue = "DAY") AttendanceReportPeriod period,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate anchor,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return attendanceService.userAudit(principal, userId, period, anchor, page, size);
    }

}
