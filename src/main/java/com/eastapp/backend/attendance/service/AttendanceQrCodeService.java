package com.eastapp.backend.attendance.service;

import com.eastapp.backend.attendance.AttendanceEventType;
import com.eastapp.backend.attendance.AttendanceQrCode;
import com.eastapp.backend.attendance.AttendanceQrCodeRepository;
import com.eastapp.backend.attendance.api.AttendanceQrCodeResponse;
import com.eastapp.backend.attendance.api.GenerateAttendanceQrCodeRequest;
import com.eastapp.backend.auth.security.AuthenticatedUser;
import com.eastapp.backend.common.error.ApiException;
import com.eastapp.backend.organisation.Tenant;
import com.eastapp.backend.organisation.TenantRepository;
import com.eastapp.backend.people.UserAccount;
import com.eastapp.backend.people.UserAccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service
public class AttendanceQrCodeService {
    private static final String PREFIX = "EASTAPP_ATTENDANCE";
    private static final Duration VALIDITY = Duration.ofMinutes(30);
    private static final int MAX_ACTIVE_PER_TYPE = 3;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final AttendanceQrCodeRepository qrCodeRepository;
    private final TenantRepository tenantRepository;
    private final UserAccountRepository userAccountRepository;

    public AttendanceQrCodeService(
            AttendanceQrCodeRepository qrCodeRepository,
            TenantRepository tenantRepository,
            UserAccountRepository userAccountRepository
    ) {
        this.qrCodeRepository = qrCodeRepository;
        this.tenantRepository = tenantRepository;
        this.userAccountRepository = userAccountRepository;
    }

    @Transactional
    public AttendanceQrCodeResponse generate(
            AuthenticatedUser principal,
            GenerateAttendanceQrCodeRequest request
    ) {
        if (!(principal.isOwner() || principal.isHead() || principal.isManager())) {
            throw forbidden("ATTENDANCE_QR_FORBIDDEN", "Only Owner, Head or Manager can generate attendance QR codes.");
        }

        // Serialise QR generation within a tenant so concurrent requests cannot
        // bypass the 3-active-codes-per-action FIFO limit.
        Tenant tenant = tenantRepository.findLockedById(principal.tenantId())
                .orElseThrow(() -> notFound("TENANT_NOT_FOUND", "Tenant not found."));
        UserAccount generator = userAccountRepository
                .findByIdAndTenant_Id(principal.userId(), principal.tenantId())
                .orElseThrow(() -> notFound("USER_NOT_FOUND", "Generator user not found."));

        Instant now = Instant.now();
        revokeOldestActiveCodesIfNeeded(tenant.getId(), request.eventType(), now);

        byte[] secretBytes = new byte[32];
        SECURE_RANDOM.nextBytes(secretBytes);
        String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(secretBytes);
        Instant expiresAt = now.plus(VALIDITY);

        AttendanceQrCode saved = qrCodeRepository.save(new AttendanceQrCode(
                tenant,
                generator,
                request.eventType(),
                sha256(secret),
                expiresAt
        ));

        return new AttendanceQrCodeResponse(
                saved.getId(),
                saved.getEventType(),
                saved.getExpiresAt(),
                payload(saved.getId(), secret)
        );
    }

    @Transactional(readOnly = true)
    public AttendanceQrCode requireUsable(
            AuthenticatedUser principal,
            String rawPayload,
            AttendanceEventType requiredEventType,
            Instant now
    ) {
        ParsedPayload parsed = parse(rawPayload);
        AttendanceQrCode code = qrCodeRepository
                .findByIdAndTenant_Id(parsed.id(), principal.tenantId())
                .orElseThrow(() -> conflict("ATTENDANCE_QR_INVALID", "This attendance QR code is invalid."));

        if (!MessageDigest.isEqual(code.getSecretHash(), sha256(parsed.secret()))) {
            throw conflict("ATTENDANCE_QR_INVALID", "This attendance QR code is invalid.");
        }
        if (code.getRevokedAt() != null) {
            throw conflict(
                    "ATTENDANCE_QR_REPLACED",
                    "This attendance QR code has been replaced by a newer code."
            );
        }
        if (!now.isBefore(code.getExpiresAt())) {
            throw conflict("ATTENDANCE_QR_EXPIRED", "This attendance QR code has expired. Generate a new code.");
        }
        if (code.getEventType() != requiredEventType) {
            String expected = requiredEventType == AttendanceEventType.CLOCK_IN ? "Check In" : "Check Out";
            throw conflict("ATTENDANCE_QR_WRONG_ACTION", "Scan a " + expected + " QR code for this attendance action.");
        }
        return code;
    }

    private void revokeOldestActiveCodesIfNeeded(
            UUID tenantId,
            AttendanceEventType eventType,
            Instant now
    ) {
        List<AttendanceQrCode> activeCodes = qrCodeRepository
                .findAllByTenant_IdAndEventTypeAndRevokedAtIsNullAndExpiresAtAfterOrderByCreatedAtAscIdAsc(
                        tenantId, eventType, now
                );

        int codesToRevoke = activeCodes.size() - (MAX_ACTIVE_PER_TYPE - 1);
        for (int index = 0; index < codesToRevoke; index++) {
            activeCodes.get(index).revoke(now);
        }
    }

    public void validateFormat(String rawPayload) {
        parse(rawPayload);
    }

    private static String payload(UUID id, String secret) {
        return PREFIX + ":" + id + ":" + secret;
    }

    private static ParsedPayload parse(String rawPayload) {
        if (rawPayload == null || rawPayload.isBlank()) {
            throw conflict("ATTENDANCE_QR_REQUIRED", "Scan a valid attendance QR code first.");
        }
        String[] parts = rawPayload.trim().split(":", 3);
        if (parts.length != 3 || !PREFIX.equals(parts[0]) || parts[2].isBlank()) {
            throw conflict("ATTENDANCE_QR_INVALID", "This attendance QR code is invalid.");
        }
        try {
            return new ParsedPayload(UUID.fromString(parts[1]), parts[2]);
        } catch (IllegalArgumentException ex) {
            throw conflict("ATTENDANCE_QR_INVALID", "This attendance QR code is invalid.");
        }
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

    private static ApiException forbidden(String code, String message) {
        return new ApiException(HttpStatus.FORBIDDEN, code, message);
    }

    private static ApiException notFound(String code, String message) {
        return new ApiException(HttpStatus.NOT_FOUND, code, message);
    }

    private record ParsedPayload(UUID id, String secret) {}
}
