package com.eastapp.backend.attendance.service;

import com.eastapp.backend.attendance.AttendanceFaceAttempt;
import com.eastapp.backend.attendance.AttendanceFaceAttemptRepository;
import com.eastapp.backend.attendance.AttendanceReportPeriod;
import com.eastapp.backend.attendance.api.AttendanceFaceAttemptResponse;
import com.eastapp.backend.attendance.api.CreateAttendanceFaceAttemptRequest;
import com.eastapp.backend.attendance.config.AttendanceProperties;
import com.eastapp.backend.auth.UserSession;
import com.eastapp.backend.auth.UserSessionRepository;
import com.eastapp.backend.auth.security.AuthenticatedUser;
import com.eastapp.backend.common.api.PageResponse;
import com.eastapp.backend.common.error.ApiException;
import com.eastapp.backend.organisation.Tenant;
import com.eastapp.backend.organisation.TenantRepository;
import com.eastapp.backend.people.UserAccount;
import com.eastapp.backend.people.UserAccountRepository;
import com.eastapp.backend.places.service.GooglePlacesService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class AttendanceFaceAttemptService {
    private static final Logger log = LoggerFactory.getLogger(AttendanceFaceAttemptService.class);
    private static final double EARTH_RADIUS_METERS = 6_371_000.0;
    private static final long MAX_IMAGE_BYTES = 5L * 1024L * 1024L;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png");

    private final AttendanceFaceAttemptRepository attemptRepository;
    private final TenantRepository tenantRepository;
    private final UserAccountRepository userAccountRepository;
    private final UserSessionRepository userSessionRepository;
    private final GooglePlacesService googlePlacesService;
    private final AttendanceProperties properties;
    private final TransactionTemplate transactionTemplate;

    public AttendanceFaceAttemptService(
            AttendanceFaceAttemptRepository attemptRepository,
            TenantRepository tenantRepository,
            UserAccountRepository userAccountRepository,
            UserSessionRepository userSessionRepository,
            GooglePlacesService googlePlacesService,
            AttendanceProperties properties,
            PlatformTransactionManager transactionManager
    ) {
        this.attemptRepository = attemptRepository;
        this.tenantRepository = tenantRepository;
        this.userAccountRepository = userAccountRepository;
        this.userSessionRepository = userSessionRepository;
        this.googlePlacesService = googlePlacesService;
        this.properties = properties;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public AttendanceFaceAttemptResponse record(
            AuthenticatedUser principal,
            CreateAttendanceFaceAttemptRequest request,
            MultipartFile photo
    ) {
        validate(request);
        String clientAttemptId = request.clientAttemptId().trim();
        AttendanceFaceAttemptResponse existing = transactionTemplate.execute(
                status -> existingResponse(principal, clientAttemptId)
        );
        if (existing != null) return existing;

        StoredPhoto storedPhoto = readPhoto(photo);
        String capturedAddress = resolveCapturedAddress(request.latitude(), request.longitude());
        try {
            AttendanceFaceAttemptResponse created = transactionTemplate.execute(
                    status -> createInTransaction(
                            principal,
                            request,
                            clientAttemptId,
                            capturedAddress,
                            storedPhoto
                    )
            );
            if (created == null) {
                throw new IllegalStateException("Attendance face-attempt transaction returned no response");
            }
            return created;
        } catch (DataIntegrityViolationException exception) {
            AttendanceFaceAttemptResponse raced = transactionTemplate.execute(
                    status -> existingResponse(principal, clientAttemptId)
            );
            if (raced != null) return raced;
            throw exception;
        }
    }

    private AttendanceFaceAttemptResponse existingResponse(
            AuthenticatedUser principal,
            String clientAttemptId
    ) {
        return attemptRepository.findByTenant_IdAndClientAttemptId(
                        principal.tenantId(), clientAttemptId
                )
                .map(attempt -> {
                    if (!attempt.getUserAccount().getId().equals(principal.userId())) {
                        throw new ApiException(
                                HttpStatus.CONFLICT,
                                "ATTENDANCE_ATTEMPT_ID_CONFLICT",
                                "This face-attempt identifier is already in use."
                        );
                    }
                    return AttendanceFaceAttemptResponse.from(attempt);
                })
                .orElse(null);
    }

    private AttendanceFaceAttemptResponse createInTransaction(
            AuthenticatedUser principal,
            CreateAttendanceFaceAttemptRequest request,
            String clientAttemptId,
            String capturedAddress,
            StoredPhoto photo
    ) {
        AttendanceFaceAttemptResponse existing = existingResponse(principal, clientAttemptId);
        if (existing != null) return existing;

        Tenant tenant = tenantRepository.findById(principal.tenantId())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "TENANT_NOT_FOUND", "Tenant not found."
                ));
        UserAccount user = userAccountRepository
                .findByIdAndTenant_Id(principal.userId(), principal.tenantId())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found."
                ));
        UserSession session = userSessionRepository.findById(principal.sessionId())
                .filter(item -> item.getUserAccount().getId().equals(principal.userId()))
                .orElse(null);

        double distanceMeters = distanceMeters(
                request.latitude(),
                request.longitude(),
                tenant.getLatitude(),
                tenant.getLongitude()
        );

        AttendanceFaceAttempt attempt = attemptRepository.save(new AttendanceFaceAttempt(
                tenant,
                user,
                session,
                clientAttemptId,
                request.intendedEventType(),
                request.deviceAttemptedAt(),
                request.latitude(),
                request.longitude(),
                request.accuracyMeters(),
                capturedAddress,
                tenant.getGooglePlaceName(),
                tenant.getFormattedAddress(),
                tenant.getLatitude(),
                tenant.getLongitude(),
                distanceMeters,
                request.failureReason(),
                request.faceCount(),
                request.faceAttemptNumber(),
                request.faceBoxWidth(),
                request.faceBoxHeight(),
                request.faceYaw(),
                request.faceRoll(),
                request.facePitch(),
                request.devicePlatform(),
                request.deviceOsVersion(),
                request.appVersion(),
                request.validationMethod(),
                photo.contentType(),
                photo.bytes()
        ));
        return AttendanceFaceAttemptResponse.from(attempt);
    }

    public PageResponse<AttendanceFaceAttemptResponse> listForUser(
            AuthenticatedUser principal,
            UUID userId,
            AttendanceReportPeriod period,
            LocalDate anchor,
            int page,
            int size
    ) {
        userAccountRepository.findByIdAndTenant_Id(userId, principal.tenantId())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found."
                ));
        ZoneId zoneId = properties.zoneId();
        LocalDate resolvedAnchor = anchor == null ? LocalDate.now(zoneId) : anchor;
        DateRange range = dateRange(period, resolvedAnchor);
        Instant fromInclusive = range.startDate().atStartOfDay(zoneId).toInstant();
        Instant toExclusive = range.endDate().plusDays(1).atStartOfDay(zoneId).toInstant();
        PageRequest pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), 100)
        );
        return PageResponse.from(
                attemptRepository.findAllByTenant_IdAndUserAccount_IdAndDeviceAttemptedAtGreaterThanEqualAndDeviceAttemptedAtLessThanOrderByDeviceAttemptedAtDesc(
                        principal.tenantId(),
                        userId,
                        fromInclusive,
                        toExclusive,
                        pageable
                ),
                AttendanceFaceAttemptResponse::from
        );
    }

    public StoredAttendanceFacePhoto loadPhoto(
            AuthenticatedUser principal,
            UUID attemptId
    ) {
        AttendanceFaceAttempt attempt = attemptRepository.findByIdAndTenant_Id(
                        attemptId, principal.tenantId()
                )
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "ATTENDANCE_FACE_ATTEMPT_NOT_FOUND",
                        "Face attempt was not found."
                ));
        if (!attempt.hasPhoto()) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "ATTENDANCE_FACE_ATTEMPT_PHOTO_NOT_FOUND",
                    "No photo was captured for this failed face attempt."
            );
        }
        return new StoredAttendanceFacePhoto(
                new ByteArrayResource(attempt.getPhotoBytes()),
                attempt.getPhotoContentType()
        );
    }

    private static void validate(CreateAttendanceFaceAttemptRequest request) {
        if (request.clientAttemptId() == null || request.clientAttemptId().trim().isEmpty()
                || request.clientAttemptId().trim().length() > 64) {
            throw badRequest("INVALID_ATTEMPT_ID", "A valid face-attempt identifier is required.");
        }
        if (request.intendedEventType() == null || request.deviceAttemptedAt() == null) {
            throw badRequest("INVALID_ATTEMPT_TIME", "Face-attempt type and time are required.");
        }
        if (request.latitude() < -90 || request.latitude() > 90
                || request.longitude() < -180 || request.longitude() > 180
                || request.accuracyMeters() < 0) {
            throw badRequest("INVALID_ATTEMPT_LOCATION", "A valid face-attempt location is required.");
        }
        if (request.failureReason() == null || request.failureReason().trim().isEmpty()
                || request.failureReason().trim().length() > 500) {
            throw badRequest("INVALID_FAILURE_REASON", "A face-verification failure reason is required.");
        }
        if (request.faceCount() < 0 || request.faceCount() > 10
                || request.faceAttemptNumber() < 1 || request.faceAttemptNumber() > 3) {
            throw badRequest("INVALID_FACE_ATTEMPT", "Face-attempt number or face count is invalid.");
        }
        requireText(request.devicePlatform(), 32, "device platform");
        requireText(request.appVersion(), 40, "app version");
        requireText(request.validationMethod(), 64, "validation method");
        if (request.deviceOsVersion() != null && request.deviceOsVersion().trim().length() > 160) {
            throw badRequest("INVALID_DEVICE_OS", "Device OS version is too long.");
        }
    }

    private static void requireText(String value, int maxLength, String label) {
        if (value == null || value.trim().isEmpty() || value.trim().length() > maxLength) {
            throw badRequest("INVALID_ATTEMPT_METADATA", "A valid " + label + " is required.");
        }
    }

    private static StoredPhoto readPhoto(MultipartFile photo) {
        if (photo == null || photo.isEmpty()) return new StoredPhoto(null, null);
        if (photo.getSize() > MAX_IMAGE_BYTES) {
            throw badRequest("ATTENDANCE_PHOTO_TOO_LARGE", "Face-attempt photo must not exceed 5 MB.");
        }
        byte[] bytes;
        try {
            bytes = photo.getBytes();
        } catch (IOException exception) {
            throw badRequest("ATTENDANCE_PHOTO_READ_FAILED", "Unable to read the face-attempt photo.");
        }
        if (bytes.length == 0 || bytes.length > MAX_IMAGE_BYTES) {
            throw badRequest("INVALID_ATTENDANCE_PHOTO_SIZE", "Face-attempt photo must be between 1 byte and 5 MB.");
        }
        String detectedType = detectImageType(bytes);
        if (!ALLOWED_CONTENT_TYPES.contains(detectedType)) {
            throw badRequest("INVALID_ATTENDANCE_PHOTO_TYPE", "Face-attempt photo must be JPEG or PNG.");
        }
        String suppliedType = normaliseContentType(photo.getContentType());
        if (!suppliedType.isBlank()
                && !suppliedType.equals("application/octet-stream")
                && !suppliedType.equals(detectedType)) {
            throw badRequest("ATTENDANCE_PHOTO_TYPE_MISMATCH", "Face-attempt photo content does not match its file type.");
        }
        return new StoredPhoto(detectedType, bytes);
    }

    private String resolveCapturedAddress(double latitude, double longitude) {
        try {
            String address = googlePlacesService.reverseGeocodeAddress(latitude, longitude);
            if (address != null && !address.isBlank()) return address;
        } catch (ApiException exception) {
            log.warn(
                    "Attendance face-attempt reverse geocoding unavailable code={} latitude={} longitude={}",
                    exception.getCode(),
                    latitude,
                    longitude
            );
        }
        return "GPS " + String.format(Locale.ROOT, "%.6f, %.6f", latitude, longitude);
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

    private static String normaliseContentType(String contentType) {
        if (contentType == null) return "";
        String value = contentType.toLowerCase(Locale.ROOT).trim();
        return value.equals("image/jpg") ? "image/jpeg" : value;
    }

    private static String detectImageType(byte[] bytes) {
        if (bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xFF
                && (bytes[1] & 0xFF) == 0xD8
                && (bytes[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (bytes.length >= 8
                && (bytes[0] & 0xFF) == 0x89
                && bytes[1] == 0x50
                && bytes[2] == 0x4E
                && bytes[3] == 0x47
                && bytes[4] == 0x0D
                && bytes[5] == 0x0A
                && bytes[6] == 0x1A
                && bytes[7] == 0x0A) {
            return "image/png";
        }
        return "";
    }

    private static ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    private record StoredPhoto(String contentType, byte[] bytes) {
    }

    private record DateRange(LocalDate startDate, LocalDate endDate) {
    }

    public record StoredAttendanceFacePhoto(Resource resource, String contentType) {
    }
}
