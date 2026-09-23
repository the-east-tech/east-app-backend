package com.eastapp.backend.stock;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StockSkuCsvRequestRepository
        extends JpaRepository<StockSkuCsvRequest, UUID> {

    List<StockSkuCsvRequest> findAllByTenantIdOrderByUpdatedAtDesc(UUID tenantId);

    boolean existsByTenantIdAndOperationAndStatus(
            UUID tenantId,
            StockSkuCsvOperation operation,
            StockSkuCsvRequestStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select request from StockSkuCsvRequest request "
            + "where request.id = :id and request.tenantId = :tenantId")
    Optional<StockSkuCsvRequest> findLockedByIdAndTenantId(
            @Param("id") UUID id,
            @Param("tenantId") UUID tenantId
    );

    long countByTenantIdAndUpdatedAtGreaterThanEqualAndUpdatedAtLessThan(
            UUID tenantId,
            Instant fromInclusive,
            Instant toExclusive
    );

    long countByTenantIdAndStatusAndUpdatedAtGreaterThanEqualAndUpdatedAtLessThan(
            UUID tenantId,
            StockSkuCsvRequestStatus status,
            Instant fromInclusive,
            Instant toExclusive
    );

    long countByTenantIdAndStatus(UUID tenantId, StockSkuCsvRequestStatus status);
}
