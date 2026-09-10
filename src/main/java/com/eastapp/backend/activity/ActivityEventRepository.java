package com.eastapp.backend.activity;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface ActivityEventRepository extends JpaRepository<ActivityEvent, UUID> {
    Page<ActivityEvent> findAllByTenantIdOrderByOccurredAtDescIdDesc(
            UUID tenantId,
            Pageable pageable
    );

    @Modifying
    @Query("delete from ActivityEvent event where event.occurredAt < :cutoff")
    int deleteOccurredBefore(@Param("cutoff") Instant cutoff);
}
