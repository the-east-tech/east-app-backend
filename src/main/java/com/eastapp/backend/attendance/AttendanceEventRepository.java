package com.eastapp.backend.attendance;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AttendanceEventRepository extends JpaRepository<AttendanceEvent, UUID> {

    @EntityGraph(attributePaths = {"tenant", "userAccount", "userAccount.role", "userSession"})
    Optional<AttendanceEvent> findByTenant_IdAndClientEventId(
            UUID tenantId,
            String clientEventId
    );

    @EntityGraph(attributePaths = {"tenant", "userAccount", "userAccount.role", "userSession"})
    List<AttendanceEvent> findAllByTenant_IdAndUserAccount_IdAndOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtAsc(
            UUID tenantId,
            UUID userId,
            Instant fromInclusive,
            Instant toExclusive
    );

    @EntityGraph(attributePaths = {"tenant", "userAccount", "userAccount.role", "userSession"})
    List<AttendanceEvent> findAllByTenant_IdAndOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtAsc(
            UUID tenantId,
            Instant fromInclusive,
            Instant toExclusive
    );
    @EntityGraph(attributePaths = {"tenant", "userAccount", "userAccount.role", "userSession"})
    Page<AttendanceEvent> findAllByTenant_IdAndUserAccount_IdAndOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtDesc(
            UUID tenantId,
            UUID userId,
            Instant fromInclusive,
            Instant toExclusive,
            Pageable pageable
    );

}
