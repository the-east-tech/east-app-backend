package com.eastapp.backend.stock;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Collection;
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

    @Query("""
            select distinct request.skuId from StockSkuChangeRequest request
            where request.tenantId = :tenantId and request.workflowStatus = :status
              and request.skuId in :skuIds
            """)
    List<UUID> submittedSkuIds(
            @Param("tenantId") UUID tenantId,
            @Param("status") StockWorkflowStatus status,
            @Param("skuIds") Collection<UUID> skuIds
    );

    boolean existsByTenantIdAndSkuIdAndWorkflowStatus(
            UUID tenantId, UUID skuId, StockWorkflowStatus workflowStatus
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
