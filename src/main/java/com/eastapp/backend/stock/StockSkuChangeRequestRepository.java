package com.eastapp.backend.stock;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.time.Instant;
import java.util.UUID;

public interface StockSkuChangeRequestRepository
        extends JpaRepository<StockSkuChangeRequest, UUID> {

    List<StockSkuChangeRequest> findAllByTenantIdOrderByUpdatedAtDesc(UUID tenantId);

    Optional<StockSkuChangeRequest> findFirstByTenantIdAndSkuIdOrderByUpdatedAtDesc(
            UUID tenantId,
            UUID skuId
    );

    Optional<StockSkuChangeRequest>
    findFirstByTenantIdAndChangeTypeAndSkuNameIgnoreCaseOrderByUpdatedAtDesc(
            UUID tenantId,
            StockSkuChangeType changeType,
            String skuName
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select request from StockSkuChangeRequest request "
            + "where request.id = :id and request.tenantId = :tenantId")
    Optional<StockSkuChangeRequest> findLockedByIdAndTenantId(
            @Param("id") UUID id,
            @Param("tenantId") UUID tenantId
    );

    long countByTenantIdAndUpdatedAtGreaterThanEqualAndUpdatedAtLessThan(
            UUID tenantId,
            Instant fromInclusive,
            Instant toExclusive
    );

    long countByTenantIdAndWorkflowStatusAndUpdatedAtGreaterThanEqualAndUpdatedAtLessThan(
            UUID tenantId,
            StockWorkflowStatus workflowStatus,
            Instant fromInclusive,
            Instant toExclusive
    );

    long countByTenantIdAndWorkflowStatus(
            UUID tenantId,
            StockWorkflowStatus workflowStatus
    );
}
