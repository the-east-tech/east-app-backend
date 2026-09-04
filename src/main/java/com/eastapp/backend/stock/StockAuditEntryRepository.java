package com.eastapp.backend.stock;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.UUID;

public interface StockAuditEntryRepository extends JpaRepository<StockAuditEntry, UUID> {
    Page<StockAuditEntry> findAllByTenant_IdAndCapturedAtGreaterThanEqualAndCapturedAtLessThanOrderByCapturedAtDesc(
            UUID tenantId,
            Instant fromInclusive,
            Instant toExclusive,
            Pageable pageable
    );
    Page<StockAuditEntry> findAllByTenant_IdAndActorEmployeeIdAndCapturedAtGreaterThanEqualAndCapturedAtLessThanOrderByCapturedAtDesc(
            UUID tenantId,
            String actorEmployeeId,
            Instant fromInclusive,
            Instant toExclusive,
            Pageable pageable
    );

}
