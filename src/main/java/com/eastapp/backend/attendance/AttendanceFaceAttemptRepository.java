package com.eastapp.backend.attendance;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AttendanceFaceAttemptRepository extends JpaRepository<AttendanceFaceAttempt, UUID> {

    @EntityGraph(attributePaths = {"tenant", "userAccount"})
    Optional<AttendanceFaceAttempt> findByTenant_IdAndClientAttemptId(
            UUID tenantId,
            String clientAttemptId
    );

    @EntityGraph(attributePaths = {"tenant", "userAccount"})
    Optional<AttendanceFaceAttempt> findByIdAndTenant_Id(UUID id, UUID tenantId);

    @EntityGraph(attributePaths = {"tenant", "userAccount"})
    Page<AttendanceFaceAttempt> findAllByTenant_IdAndUserAccount_IdAndDeviceAttemptedAtGreaterThanEqualAndDeviceAttemptedAtLessThanOrderByDeviceAttemptedAtDesc(
            UUID tenantId,
            UUID userId,
            Instant fromInclusive,
            Instant toExclusive,
            Pageable pageable
    );
}
