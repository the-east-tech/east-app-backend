package com.eastapp.backend.attendance;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AttendanceQrCodeRepository extends JpaRepository<AttendanceQrCode, UUID> {

    @EntityGraph(attributePaths = {"tenant", "generatedByUser"})
    Optional<AttendanceQrCode> findByIdAndTenant_Id(UUID id, UUID tenantId);

    List<AttendanceQrCode>
    findAllByTenant_IdAndEventTypeAndRevokedAtIsNullAndExpiresAtAfterOrderByCreatedAtAscIdAsc(
            UUID tenantId,
            AttendanceEventType eventType,
            Instant now
    );
}

