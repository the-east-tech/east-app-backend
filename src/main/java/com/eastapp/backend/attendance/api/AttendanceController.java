package com.eastapp.backend.attendance.api;

import com.eastapp.backend.attendance.AttendanceReportPeriod;
import com.eastapp.backend.attendance.AttendanceEventType;
import com.eastapp.backend.attendance.service.AttendanceFaceAttemptService;
import com.eastapp.backend.attendance.service.AttendanceService;
import com.eastapp.backend.common.api.PageResponse;
import com.eastapp.backend.auth.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/attendance")
public class AttendanceController {

    private final AttendanceService attendanceService;
    private final AttendanceFaceAttemptService attendanceFaceAttemptService;

    public AttendanceController(
            AttendanceService attendanceService,
            AttendanceFaceAttemptService attendanceFaceAttemptService
    ) {
        this.attendanceService = attendanceService;
        this.attendanceFaceAttemptService = attendanceFaceAttemptService;
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

    @PostMapping(value = "/face-attempts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<AttendanceFaceAttemptResponse> createFaceAttempt(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam String clientAttemptId,
            @RequestParam AttendanceEventType intendedEventType,
            @RequestParam Instant deviceAttemptedAt,
            @RequestParam double latitude,
            @RequestParam double longitude,
            @RequestParam double accuracyMeters,
            @RequestParam String failureReason,
            @RequestParam int faceCount,
            @RequestParam int faceAttemptNumber,
            @RequestParam(required = false) Double faceBoxWidth,
            @RequestParam(required = false) Double faceBoxHeight,
            @RequestParam(required = false) Double faceYaw,
            @RequestParam(required = false) Double faceRoll,
            @RequestParam(required = false) Double facePitch,
            @RequestParam String devicePlatform,
            @RequestParam(required = false) String deviceOsVersion,
            @RequestParam String appVersion,
            @RequestParam String validationMethod,
            @RequestParam(required = false) MultipartFile photo
    ) {
        CreateAttendanceFaceAttemptRequest request = new CreateAttendanceFaceAttemptRequest(
                clientAttemptId, intendedEventType, deviceAttemptedAt,
                latitude, longitude, accuracyMeters, failureReason,
                faceCount, faceAttemptNumber, faceBoxWidth, faceBoxHeight,
                faceYaw, faceRoll, facePitch, devicePlatform, deviceOsVersion,
                appVersion, validationMethod
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(attendanceFaceAttemptService.record(principal, request, photo));
    }

    @GetMapping("/users/{userId}/face-attempts")
    @PreAuthorize("hasAnyRole('OWNER', 'HEAD')")
    PageResponse<AttendanceFaceAttemptResponse> userFaceAttempts(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "DAY") AttendanceReportPeriod period,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate anchor,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return attendanceFaceAttemptService.listForUser(
                principal, userId, period, anchor, page, size
        );
    }

    @GetMapping("/face-attempts/{attemptId}/photo")
    @PreAuthorize("hasAnyRole('OWNER', 'HEAD')")
    ResponseEntity<Resource> faceAttemptPhoto(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID attemptId
    ) {
        AttendanceFaceAttemptService.StoredAttendanceFacePhoto photo =
                attendanceFaceAttemptService.loadPhoto(principal, attemptId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(photo.contentType()))
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
                .body(photo.resource());
    }

    @GetMapping("/audit")
    @PreAuthorize("hasAnyRole('OWNER', 'HEAD')")
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
    @PreAuthorize("hasAnyRole('OWNER', 'HEAD')")
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
