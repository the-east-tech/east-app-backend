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

public interface StockReceivingRepository extends JpaRepository<StockReceiving, UUID> {
    @EntityGraph(attributePaths = {"tenant", "supplier", "receivedBy", "reviewedBy"})
    Page<StockReceiving> findAllByTenant_IdOrderByCapturedAtDesc(UUID tenantId, Pageable pageable);

    @EntityGraph(attributePaths = {"tenant", "supplier", "receivedBy", "reviewedBy"})
    @Query("""
            select receiving
            from StockReceiving receiving
            where receiving.tenant.id = :tenantId
              and (:filterByReviewStatus = false or receiving.reviewStatus =
                    case :reviewStatus
                        when 'Pending Review' then 'SUBMITTED'
                        when 'Pending' then 'SUBMITTED'
                        when 'Approved' then 'DONE'
                        when 'Rejected' then 'PENDING'
                        else :reviewStatus
                    end)
              and (:filterByFrom = false or receiving.capturedAt >= :fromInclusive)
              and (:filterByTo = false or receiving.capturedAt < :toExclusive)
            order by receiving.capturedAt desc, receiving.id desc
            """)
    Page<StockReceiving> searchByTenant(
            @Param("tenantId") UUID tenantId,
            @Param("filterByReviewStatus") boolean filterByReviewStatus,
            @Param("reviewStatus") String reviewStatus,
            @Param("filterByFrom") boolean filterByFrom,
            @Param("fromInclusive") Instant fromInclusive,
            @Param("filterByTo") boolean filterByTo,
            @Param("toExclusive") Instant toExclusive,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"tenant", "supplier", "receivedBy", "reviewedBy", "items", "items.sku"})
    Optional<StockReceiving> findByIdAndTenant_Id(UUID id, UUID tenantId);

    boolean existsByTenant_IdAndSupplier_Id(UUID tenantId, UUID supplierId);

    long countByTenant_IdAndCapturedAtGreaterThanEqualAndCapturedAtLessThan(
            UUID tenantId,
            Instant fromInclusive,
            Instant toExclusive
    );

    @Query("""
            select count(receiving)
            from StockReceiving receiving
            where receiving.tenant.id = :tenantId
              and receiving.reviewStatus =
                    case :reviewStatus
                        when 'Pending Review' then 'SUBMITTED'
                        when 'Pending' then 'SUBMITTED'
                        when 'Approved' then 'DONE'
                        when 'Rejected' then 'PENDING'
                        else :reviewStatus
                    end
              and receiving.capturedAt >= :fromInclusive
              and receiving.capturedAt < :toExclusive
            """)
    long countByTenant_IdAndReviewStatusAndCapturedAtGreaterThanEqualAndCapturedAtLessThan(
            @Param("tenantId") UUID tenantId,
            @Param("reviewStatus") String reviewStatus,
            @Param("fromInclusive") Instant fromInclusive,
            @Param("toExclusive") Instant toExclusive
    );
}
