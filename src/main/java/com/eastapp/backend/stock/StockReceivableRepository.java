package com.eastapp.backend.stock;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StockReceivableRepository extends JpaRepository<StockReceivable, UUID> {
    @EntityGraph(attributePaths = {"tenant", "supplier", "receivedBy", "reviewedBy"})
    Page<StockReceivable> findAllByTenant_IdOrderByCapturedAtDesc(UUID tenantId, Pageable pageable);

    @EntityGraph(attributePaths = {"tenant", "supplier", "receivedBy", "reviewedBy"})
    @Query("""
            select receivable
            from StockReceivable receivable
            where receivable.tenant.id = :tenantId
              and (:filterByWorkflowStatus = false or receivable.workflowStatus = :workflowStatus)
            order by receivable.capturedAt desc, receivable.id desc
            """)
    Page<StockReceivable> searchByTenant(
            @Param("tenantId") UUID tenantId,
            @Param("filterByWorkflowStatus") boolean filterByWorkflowStatus,
            @Param("workflowStatus") StockWorkflowStatus workflowStatus,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"tenant", "supplier", "receivedBy", "reviewedBy", "items", "items.sku"})
    Optional<StockReceivable> findByIdAndTenant_Id(UUID id, UUID tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select receivable from StockReceivable receivable where receivable.id = :id and receivable.tenant.id = :tenantId")
    Optional<StockReceivable> findLockedByIdAndTenantId(
            @Param("id") UUID id, @Param("tenantId") UUID tenantId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select receivable from StockReceivable receivable where receivable.tenant.id = :tenantId and receivable.supplier.id = :supplierId")
    Optional<StockReceivable> findLockedByTenantIdAndSupplierId(
            @Param("tenantId") UUID tenantId,
            @Param("supplierId") UUID supplierId
    );

    boolean existsByTenant_IdAndSupplier_Id(UUID tenantId, UUID supplierId);

    boolean existsByTenant_IdAndItems_Sku_Id(UUID tenantId, UUID skuId);

    @Query("""
            select distinct item.sku.id from StockReceivable receivable join receivable.items item
            where receivable.tenant.id = :tenantId and receivable.workflowStatus = :status
              and item.sku.id in :skuIds
            """)
    List<UUID> submittedSkuIds(
            @Param("tenantId") UUID tenantId,
            @Param("status") StockWorkflowStatus status,
            @Param("skuIds") Collection<UUID> skuIds
    );

    @Query("""
            select count(receivable) > 0
            from StockReceivable receivable join receivable.items item
            where receivable.tenant.id = :tenantId
              and item.sku.id = :skuId
              and receivable.workflowStatus = :status
            """)
    boolean hasSkuAwaitingReview(
            @Param("tenantId") UUID tenantId,
            @Param("skuId") UUID skuId,
            @Param("status") StockWorkflowStatus status
    );

    long countByTenant_IdAndCapturedAtGreaterThanEqualAndCapturedAtLessThan(
            UUID tenantId,
            Instant fromInclusive,
            Instant toExclusive
    );

    long countByTenant_IdAndWorkflowStatusAndCapturedAtGreaterThanEqualAndCapturedAtLessThan(
            UUID tenantId,
            StockWorkflowStatus workflowStatus,
            Instant fromInclusive,
            Instant toExclusive
    );

    long countByTenant_IdAndWorkflowStatus(UUID tenantId, StockWorkflowStatus workflowStatus);
}
