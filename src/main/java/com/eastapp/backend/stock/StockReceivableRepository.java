package com.eastapp.backend.stock;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
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
              and (:filterByFrom = false or receivable.capturedAt >= :fromInclusive)
              and (:filterByTo = false or receivable.capturedAt < :toExclusive)
            order by receivable.capturedAt desc, receivable.id desc
            """)
    Page<StockReceivable> searchByTenant(
            @Param("tenantId") UUID tenantId,
            @Param("filterByWorkflowStatus") boolean filterByWorkflowStatus,
            @Param("workflowStatus") StockWorkflowStatus workflowStatus,
            @Param("filterByFrom") boolean filterByFrom,
            @Param("fromInclusive") Instant fromInclusive,
            @Param("filterByTo") boolean filterByTo,
            @Param("toExclusive") Instant toExclusive,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"tenant", "supplier", "receivedBy", "reviewedBy", "items", "items.sku"})
    Optional<StockReceivable> findByIdAndTenant_Id(UUID id, UUID tenantId);

    boolean existsByTenant_IdAndSupplier_Id(UUID tenantId, UUID supplierId);

    boolean existsByTenant_IdAndItems_Sku_Id(UUID tenantId, UUID skuId);

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
