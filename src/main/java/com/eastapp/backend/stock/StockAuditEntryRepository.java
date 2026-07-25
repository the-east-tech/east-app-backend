package com.eastapp.backend.stock;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.UUID;

public interface StockAuditEntryRepository extends JpaRepository<StockAuditEntry, UUID> {
    @EntityGraph(attributePaths = {"tenant", "changes"})
    Page<StockAuditEntry> findAllByTenant_IdAndCapturedAtGreaterThanEqualAndCapturedAtLessThanOrderByCapturedAtDesc(
            UUID tenantId,
            Instant fromInclusive,
            Instant toExclusive,
            Pageable pageable
    );
}
