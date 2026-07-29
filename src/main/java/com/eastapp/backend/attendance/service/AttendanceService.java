package com.eastapp.backend.attendance.service;

import com.eastapp.backend.attendance.AttendanceEvent;
import com.eastapp.backend.attendance.AttendanceEventRepository;
import com.eastapp.backend.attendance.AttendanceEventType;
import com.eastapp.backend.attendance.AttendanceReportPeriod;
import com.eastapp.backend.attendance.api.AttendanceAuditResponse;
import com.eastapp.backend.attendance.api.AttendanceAuditSummaryResponse;
import com.eastapp.backend.attendance.api.AttendanceEventResponse;
import com.eastapp.backend.attendance.api.AttendanceTodayResponse;
import com.eastapp.backend.attendance.api.AttendanceUserAuditResponse;
import com.eastapp.backend.attendance.api.AttendanceUserDetailResponse;
import com.eastapp.backend.common.api.PageResponse;
import com.eastapp.backend.attendance.api.CreateAttendanceEventRequest;
import com.eastapp.backend.attendance.config.AttendanceProperties;
import com.eastapp.backend.organisation.Tenant;
import com.eastapp.backend.organisation.TenantRepository;
import com.eastapp.backend.people.UserAccount;
import com.eastapp.backend.people.UserAccountRepository;
import com.eastapp.backend.places.service.GooglePlacesService;
import com.eastapp.backend.auth.UserSession;
import com.eastapp.backend.auth.UserSessionRepository;
import com.eastapp.backend.auth.security.AuthenticatedUser;
import com.eastapp.backend.common.error.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class AttendanceService {

    private static final Logger log = LoggerFactory.getLogger(AttendanceService.class);
    private static final double EARTH_RADIUS_METERS = 6_371_000.0;
    private static final DateTimeFormatter DAY_LABEL =
            DateTimeFormatter.ofPattern("d MMM uuuu", Locale.ENGLISH);
    private static final DateTimeFormatter MONTH_LABEL =
            DateTimeFormatter.ofPattern("MMMM uuuu", Locale.ENGLISH);
    private static final DateTimeFormatter TIME_LABEL =
            DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);

    private final AttendanceEventRepository attendanceEventRepository;
    private final TenantRepository tenantRepository;
    private final UserAccountRepository userAccountRepository;
    private final UserSessionRepository userSessionRepository;
    private final AttendanceProperties properties;
    private final GooglePlacesService googlePlacesService;
    private final TransactionTemplate transactionTemplate;

    public AttendanceService(
            AttendanceEventRepository attendanceEventRepository,
            TenantRepository tenantRepository,
            UserAccountRepository userAccountRepository,
            UserSessionRepository userSessionRepository,
            AttendanceProperties properties,
            GooglePlacesService googlePlacesService,
            PlatformTransactionManager transactionManager
    ) {
        this.attendanceEventRepository = attendanceEventRepository;
        this.tenantRepository = tenantRepository;
        this.userAccountRepository = userAccountRepository;
        this.userSessionRepository = userSessionRepository;
        this.properties = properties;
        this.googlePlacesService = googlePlacesService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * Reverse geocoding happens outside the database transaction. A slow Google
     * response therefore never occupies one of the five Railway DB connections.
     */
    public AttendanceEventResponse create(
            AuthenticatedUser principal,
            CreateAttendanceEventRequest request
    ) {
        assertCaptureProof(request);
        String clientEventId = request.clientEventId().trim();

        AttendanceEventResponse existing = transactionTemplate.execute(
                status -> existingResponse(principal, clientEventId)
        );
        if (existing != null) {
            return existing;
        }

        String capturedAddress = resolveCapturedAddress(
                request.latitude(),
                request.longitude()
        );
        AttendanceEventResponse created = transactionTemplate.execute(
                status -> createInTransaction(principal, request, clientEventId, capturedAddress)
        );
        if (created == null) {
            throw new IllegalStateException("Attendance transaction returned no response");
        }
        return created;
    }

    private AttendanceEventResponse existingResponse(
            AuthenticatedUser principal,
            String clientEventId
    ) {
        return attendanceEventRepository.findByTenant_IdAndClientEventId(
                        principal.tenantId(), clientEventId
                )
                .map(event -> {
                    if (!event.getUserAccount().getId().equals(principal.userId())) {
                        throw conflict(
                                "ATTENDANCE_EVENT_ID_CONFLICT",
                                "This attendance event identifier is already in use."
                        );
                    }
                    return AttendanceEventResponse.from(event);
                })
                .orElse(null);
    }

    private AttendanceEventResponse createInTransaction(
            AuthenticatedUser principal,
            CreateAttendanceEventRequest request,
            String clientEventId,
            String capturedAddress
    ) {
        AttendanceEventResponse existing = existingResponse(principal, clientEventId);
        if (existing != null) {
            return existing;
        }

        Tenant tenant = tenantRepository.findById(principal.tenantId())
                .orElseThrow(() -> notFound("TENANT_NOT_FOUND", "Tenant not found."));
        UserAccount user = userAccountRepository
                .findByIdAndTenant_Id(principal.userId(), principal.tenantId())
                .orElseThrow(() -> notFound("USER_NOT_FOUND", "User not found."));
        UserSession userSession = userSessionRepository.findById(principal.sessionId())
                .filter(session -> session.getUserAccount().getId().equals(principal.userId()))
                .orElse(null);

        ZoneId zoneId = properties.zoneId();
        LocalDate today = LocalDate.now(zoneId);
        TimeRange todayRange = timeRange(today, today, zoneId);
        List<AttendanceEvent> todayEvents = attendanceEventRepository
                .findAllByTenant_IdAndUserAccount_IdAndOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtAsc(
                        principal.tenantId(),
                        principal.userId(),
                        todayRange.fromInclusive(),
                        todayRange.toExclusive()
                );
        assertValidSequence(request.eventType(), todayEvents);

        double distanceMeters = distanceMeters(
                request.latitude(),
                request.longitude(),
                tenant.getLatitude(),
                tenant.getLongitude()
        );

        AttendanceEvent event = new AttendanceEvent(
                tenant,
                user,
                userSession,
                clientEventId,
                request.eventType(),
                request.deviceCapturedAt(),
                request.latitude(),
                request.longitude(),
                request.accuracyMeters(),
                capturedAddress,
                tenant.getGooglePlaceName(),
                tenant.getFormattedAddress(),
                tenant.getLatitude(),
                tenant.getLongitude(),
                distanceMeters,
                request.cameraCaptureValid(),
                request.faceValid(),
                request.faceCount(),
                request.faceAttemptCount(),
                request.faceVerificationBypassed(),
                request.faceBoxWidth(),
                request.faceBoxHeight(),
                request.faceYaw(),
                request.faceRoll(),
                request.facePitch(),
                request.qrCheckpointValid(),
                request.devicePlatform(),
                request.deviceOsVersion(),
                request.appVersion(),
                request.validationMethod()
        );

        return AttendanceEventResponse.from(attendanceEventRepository.save(event));
    }

    @Transactional(readOnly = true)
    public AttendanceTodayResponse today(AuthenticatedUser principal) {
        ZoneId zoneId = properties.zoneId();
        LocalDate today = LocalDate.now(zoneId);
        TimeRange range = timeRange(today, today, zoneId);
        List<AttendanceEvent> events = attendanceEventRepository
                .findAllByTenant_IdAndUserAccount_IdAndOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtAsc(
                        principal.tenantId(),
                        principal.userId(),
                        range.fromInclusive(),
                        range.toExclusive()
                );
        return toTodayResponse(today, events);
    }

    @Transactional(readOnly = true)
    public AttendanceAuditResponse audit(
            AuthenticatedUser principal,
            AttendanceReportPeriod period,
            LocalDate anchor
    ) {
        ZoneId zoneId = properties.zoneId();
        LocalDate resolvedAnchor = anchor == null ? LocalDate.now(zoneId) : anchor;
        DateRange dateRange = dateRange(period, resolvedAnchor);
        TimeRange timeRange = timeRange(dateRange.startDate(), dateRange.endDate(), zoneId);

        List<UserAccount> users = userAccountRepository
                .findAllByTenant_IdOrderByIdentity_FullNameAsc(principal.tenantId())
                .stream()
                .filter(user -> employmentOverlaps(user, dateRange))
                .toList();

        List<AttendanceEvent> events = attendanceEventRepository
                .findAllByTenant_IdAndOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtAsc(
                        principal.tenantId(),
                        timeRange.fromInclusive(),
                        timeRange.toExclusive()
                );

        Map<UUID, List<AttendanceEvent>> eventsByUser = new HashMap<>();
        for (AttendanceEvent event : events) {
            eventsByUser.computeIfAbsent(
                    event.getUserAccount().getId(),
                    ignored -> new ArrayList<>()
            ).add(event);
        }

        List<AttendanceUserAuditResponse> userReports = users.stream()
                .map(user -> buildUserReport(
                        user,
                        eventsByUser.getOrDefault(user.getId(), List.of()),
                        dateRange,
                        period,
                        zoneId
                ))
                .sorted(Comparator
                        .comparingInt((AttendanceUserAuditResponse item) -> statusPriority(item.status()))
                        .thenComparing(AttendanceUserAuditResponse::fullName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        int peopleWithAttendance = userReports.stream()
                .mapToInt(item -> item.presentDays() > 0 ? 1 : 0)
                .sum();
        int presentDays = userReports.stream()
                .mapToInt(AttendanceUserAuditResponse::presentDays)
                .sum();
        int completedDays = userReports.stream()
                .mapToInt(AttendanceUserAuditResponse::completedDays)
                .sum();
        int missingCheckOutDays = userReports.stream()
                .mapToInt(AttendanceUserAuditResponse::missingCheckOutDays)
                .sum();
        AttendanceAuditSummaryResponse summary = new AttendanceAuditSummaryResponse(
                users.size(),
                peopleWithAttendance,
                presentDays,
                completedDays,
                missingCheckOutDays,
                percentage(completedDays, presentDays)
        );

        return new AttendanceAuditResponse(
                period,
                dateRange.startDate(),
                dateRange.endDate(),
                periodLabel(period, dateRange),
                summary,
                userReports
        );
    }

    @Transactional(readOnly = true)
    public AttendanceUserDetailResponse userAudit(
            AuthenticatedUser principal,
            UUID userId,
            AttendanceReportPeriod period,
            LocalDate anchor,
            int page,
            int size
    ) {
        ZoneId zoneId = properties.zoneId();
        LocalDate resolvedAnchor = anchor == null ? LocalDate.now(zoneId) : anchor;
        DateRange dateRange = dateRange(period, resolvedAnchor);
        TimeRange timeRange = timeRange(dateRange.startDate(), dateRange.endDate(), zoneId);

        UserAccount user = userAccountRepository
                .findByIdAndTenant_Id(userId, principal.tenantId())
                .orElseThrow(() -> notFound("USER_NOT_FOUND", "User not found."));

        List<AttendanceEvent> periodEvents = attendanceEventRepository
                .findAllByTenant_IdAndUserAccount_IdAndOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtAsc(
                        principal.tenantId(),
                        userId,
                        timeRange.fromInclusive(),
                        timeRange.toExclusive()
                );

        AttendanceUserAuditResponse summary = buildUserReport(
                user,
                periodEvents,
                dateRange,
                period,
                zoneId
        );

        PageRequest pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), 100)
        );
        PageResponse<AttendanceEventResponse> events = PageResponse.from(
                attendanceEventRepository
                        .findAllByTenant_IdAndUserAccount_IdAndOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtDesc(
                                principal.tenantId(),
                                userId,
                                timeRange.fromInclusive(),
                                timeRange.toExclusive(),
                                pageable
                        ),
                AttendanceEventResponse::from
        );

        return new AttendanceUserDetailResponse(
                period,
                dateRange.startDate(),
                dateRange.endDate(),
                periodLabel(period, dateRange),
                summary,
                events
        );
    }

    private AttendanceTodayResponse toTodayResponse(
            LocalDate date,
            List<AttendanceEvent> events
    ) {
        AttendanceEvent clockIn = null;
        AttendanceEvent clockOut = null;
        Instant openClockIn = null;
        long totalMinutes = 0;
        for (AttendanceEvent event : events) {
            if (event.getEventType() == AttendanceEventType.CLOCK_IN) {
                if (clockIn == null) {
                    clockIn = event;
                }
                openClockIn = event.getOccurredAt();
            } else if (openClockIn != null) {
                clockOut = event;
                totalMinutes += positiveMinutes(openClockIn, event.getOccurredAt());
                openClockIn = null;
            }
        }

        String status;
        if (clockIn == null) {
            status = "NOT_STARTED";
        } else if (openClockIn != null) {
            status = "WORKING";
        } else {
            status = "COMPLETED";
        }

        return new AttendanceTodayResponse(
                date,
                status,
                clockIn == null ? null : AttendanceEventResponse.from(clockIn),
                clockOut == null ? null : AttendanceEventResponse.from(clockOut),
                totalMinutes
        );
    }

    private AttendanceUserAuditResponse buildUserReport(
            UserAccount user,
            List<AttendanceEvent> events,
            DateRange dateRange,
            AttendanceReportPeriod period,
            ZoneId zoneId
    ) {
        Map<LocalDate, List<AttendanceEvent>> eventsByDay = new LinkedHashMap<>();
        for (AttendanceEvent event : events) {
            LocalDate localDate = event.getOccurredAt().atZone(zoneId).toLocalDate();
            eventsByDay.computeIfAbsent(localDate, ignored -> new ArrayList<>()).add(event);
        }

        int presentDays = 0;
        int completedDays = 0;
        int missingCheckOutDays = 0;
        int validEvents = 0;
        long totalCompletedMinutes = 0;
        int totalFirstClockInMinutes = 0;
        int clockInDayCount = 0;
        Instant firstClockInAt = null;
        Instant lastClockOutAt = null;
        for (List<AttendanceEvent> dayEvents : eventsByDay.values()) {
            DaySummary day = summariseDay(dayEvents, zoneId);
            if (day.present()) {
                presentDays++;
                clockInDayCount++;
                totalFirstClockInMinutes += day.firstClockInMinuteOfDay();
                firstClockInAt = earlier(firstClockInAt, day.firstClockInAt());
            }
            if (day.completed()) {
                completedDays++;
                totalCompletedMinutes += day.workingMinutes();
                lastClockOutAt = later(lastClockOutAt, day.lastClockOutAt());
            }
            if (day.missingClockOut()) {
                missingCheckOutDays++;
            }
            validEvents += day.validEvents();
        }

        String status;
        if (presentDays == 0) {
            status = "NO_RECORD";
        } else if (missingCheckOutDays > 0) {
            LocalDate today = LocalDate.now(zoneId);
            status = period == AttendanceReportPeriod.DAY
                    && dateRange.startDate().equals(today)
                    ? "WORKING"
                    : "MISSING_OUT";
        } else {
            status = "COMPLETED";
        }

        String averageClockInTime = null;
        if (clockInDayCount > 0) {
            int averageMinutes = Math.round((float) totalFirstClockInMinutes / clockInDayCount);
            averageClockInTime = LocalTime.of(
                    Math.floorDiv(averageMinutes, 60) % 24,
                    Math.floorMod(averageMinutes, 60)
            ).format(TIME_LABEL);
        }

        Long averageWorkingMinutes = completedDays == 0
                ? null
                : Math.round((double) totalCompletedMinutes / completedDays);

        return new AttendanceUserAuditResponse(
                user.getId(),
                user.getEmployeeId(),
                user.getFullName(),
                user.getRole().getName(),
                user.isActive(),
                status,
                presentDays,
                completedDays,
                missingCheckOutDays,
                validEvents,
                firstClockInAt,
                lastClockOutAt,
                averageClockInTime,
                averageWorkingMinutes,
                percentage(completedDays, presentDays)
        );
    }

    private DaySummary summariseDay(List<AttendanceEvent> events, ZoneId zoneId) {
        Instant firstClockInAt = null;
        Instant lastClockOutAt = null;
        Instant openClockIn = null;
        long workingMinutes = 0;
        boolean missingClockOut = false;
        int validEvents = 0;

        for (AttendanceEvent event : events) {
            if (event.isCameraCaptureValid()
                    && event.isFaceValid()
                    && event.getFaceCount() == 1
                    && event.isQrCheckpointValid()) {
                validEvents++;
            }

            if (event.getEventType() == AttendanceEventType.CLOCK_IN) {
                if (firstClockInAt == null) {
                    firstClockInAt = event.getOccurredAt();
                }
                if (openClockIn != null) {
                    missingClockOut = true;
                }
                openClockIn = event.getOccurredAt();
            } else if (openClockIn != null) {
                lastClockOutAt = event.getOccurredAt();
                workingMinutes += positiveMinutes(openClockIn, event.getOccurredAt());
                openClockIn = null;
            }
        }

        if (openClockIn != null) {
            missingClockOut = true;
        }

        int firstClockInMinuteOfDay = firstClockInAt == null
                ? 0
                : firstClockInAt.atZone(zoneId).toLocalTime().getHour() * 60
                + firstClockInAt.atZone(zoneId).toLocalTime().getMinute();

        return new DaySummary(
                firstClockInAt != null,
                firstClockInAt != null && lastClockOutAt != null,
                missingClockOut,
                workingMinutes,
                firstClockInAt,
                lastClockOutAt,
                firstClockInMinuteOfDay,
                validEvents
        );
    }

    private static void assertCaptureProof(CreateAttendanceEventRequest request) {
        if (!request.cameraCaptureValid()) {
            throw badRequest(
                    "CAMERA_CAPTURE_REQUIRED",
                    "Attendance requires a live camera capture."
            );
        }
        boolean facePassed = request.faceValid()
                && request.faceCount() == 1
                && !request.faceVerificationBypassed();
        boolean bypassAllowed = !request.faceValid()
                && request.faceVerificationBypassed()
                && request.faceAttemptCount() == 3;
        if (!facePassed && !bypassAllowed) {
            throw badRequest(
                    "FACE_VERIFICATION_REQUIRED",
                    "Face verification must pass, or attendance may continue only after three failed attempts."
            );
        }
        if (!request.qrCheckpointValid()) {
            throw badRequest(
                    "QR_CHECKPOINT_REQUIRED",
                    "Attendance requires the checkpoint QR validation."
            );
        }
    }

    private static void assertValidSequence(
            AttendanceEventType requestedType,
            List<AttendanceEvent> todayEvents
    ) {
        AttendanceEventType latestType = todayEvents.isEmpty()
                ? null
                : todayEvents.get(todayEvents.size() - 1).getEventType();

        if (requestedType == AttendanceEventType.CLOCK_IN
                && latestType == AttendanceEventType.CLOCK_IN) {
            throw conflict("ALREADY_CLOCKED_IN", "A clock-in is already active today.");
        }
        if (requestedType == AttendanceEventType.CLOCK_OUT
                && latestType != AttendanceEventType.CLOCK_IN) {
            throw conflict("CLOCK_IN_REQUIRED", "Clock in before clocking out.");
        }
    }

    private static boolean employmentOverlaps(UserAccount user, DateRange range) {
        return (user.getStartDate() == null || !user.getStartDate().isAfter(range.endDate()))
                && (user.getEndDate() == null || !user.getEndDate().isBefore(range.startDate()));
    }

    private static DateRange dateRange(AttendanceReportPeriod period, LocalDate anchor) {
        return switch (period) {
            case DAY -> new DateRange(anchor, anchor);
            case WEEK -> {
                LocalDate start = anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                yield new DateRange(start, start.plusDays(6));
            }
            case MONTH -> new DateRange(
                    anchor.with(TemporalAdjusters.firstDayOfMonth()),
                    anchor.with(TemporalAdjusters.lastDayOfMonth())
            );
            case YEAR -> new DateRange(
                    anchor.with(TemporalAdjusters.firstDayOfYear()),
                    anchor.with(TemporalAdjusters.lastDayOfYear())
            );
        };
    }

    private static TimeRange timeRange(LocalDate startDate, LocalDate endDate, ZoneId zoneId) {
        return new TimeRange(
                startDate.atStartOfDay(zoneId).toInstant(),
                endDate.plusDays(1).atStartOfDay(zoneId).toInstant()
        );
    }

    private static String periodLabel(AttendanceReportPeriod period, DateRange range) {
        return switch (period) {
            case DAY -> range.startDate().format(DAY_LABEL);
            case WEEK -> range.startDate().format(DAY_LABEL)
                    + " – "
                    + range.endDate().format(DAY_LABEL);
            case MONTH -> range.startDate().format(MONTH_LABEL);
            case YEAR -> Integer.toString(range.startDate().getYear());
        };
    }

    private static int statusPriority(String status) {
        return switch (status) {
            case "MISSING_OUT", "WORKING" -> 0;
            case "COMPLETED" -> 1;
            default -> 2;
        };
    }

    private static double percentage(int numerator, int denominator) {
        if (denominator <= 0) {
            return 0.0;
        }
        return Math.round((numerator * 10_000.0) / denominator) / 100.0;
    }

    private static long positiveMinutes(Instant from, Instant to) {
        if (from == null || to == null || !to.isAfter(from)) {
            return 0;
        }
        return Duration.between(from, to).toMinutes();
    }

    private static Instant earlier(Instant current, Instant candidate) {
        if (candidate == null) return current;
        if (current == null || candidate.isBefore(current)) return candidate;
        return current;
    }

    private static Instant later(Instant current, Instant candidate) {
        if (candidate == null) return current;
        if (current == null || candidate.isAfter(current)) return candidate;
        return current;
    }

    private String resolveCapturedAddress(double latitude, double longitude) {
        try {
            String address = googlePlacesService.reverseGeocodeAddress(latitude, longitude);
            if (address != null && !address.isBlank()) {
                return address;
            }
        } catch (ApiException exception) {
            log.warn(
                    "Attendance reverse geocoding unavailable code={} latitude={} longitude={}",
                    exception.getCode(),
                    latitude,
                    longitude
            );
        }
        return "Address unavailable · "
                + String.format(Locale.ROOT, "%.6f, %.6f", latitude, longitude);
    }

    private static double distanceMeters(
            double latitude,
            double longitude,
            double targetLatitude,
            double targetLongitude
    ) {
        double latitudeRadians = Math.toRadians(latitude);
        double targetLatitudeRadians = Math.toRadians(targetLatitude);
        double latitudeDelta = Math.toRadians(targetLatitude - latitude);
        double longitudeDelta = Math.toRadians(targetLongitude - longitude);

        double a = Math.sin(latitudeDelta / 2) * Math.sin(latitudeDelta / 2)
                + Math.cos(latitudeRadians)
                * Math.cos(targetLatitudeRadians)
                * Math.sin(longitudeDelta / 2)
                * Math.sin(longitudeDelta / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_METERS * c;
    }

    private static ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    private static ApiException notFound(String code, String message) {
        return new ApiException(HttpStatus.NOT_FOUND, code, message);
    }

    private static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

    private record DateRange(LocalDate startDate, LocalDate endDate) {
    }

    private record TimeRange(Instant fromInclusive, Instant toExclusive) {
    }

    private record DaySummary(
            boolean present,
            boolean completed,
            boolean missingClockOut,
            long workingMinutes,
            Instant firstClockInAt,
            Instant lastClockOutAt,
            int firstClockInMinuteOfDay,
            int validEvents
    ) {
    }
}
