package com.eastapp.backend.stock;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StockCountSubmissionRepository extends JpaRepository<StockCountSubmission, UUID> {
    @EntityGraph(attributePaths = {"tenant", "sku", "submittedBy", "reviewedBy"})
    Page<StockCountSubmission> findAllByTenant_IdOrderByCapturedAtDesc(UUID tenantId, Pageable pageable);

    @EntityGraph(attributePaths = {"tenant", "sku", "submittedBy", "reviewedBy"})
    @Query("""
            select submission
            from StockCountSubmission submission
            where submission.tenant.id = :tenantId
              and (:filterBySubmittedBy = false or submission.submittedBy.id = :submittedByUserId)
              and (
                    (:filterByReviewStatus = false and submission.reviewStatus <> 'PENDING')
                    or (:filterByReviewStatus = true and submission.reviewStatus =
                        case :reviewStatus
                            when 'Pending Review' then 'SUBMITTED'
                            when 'Pending' then 'SUBMITTED'
                            when 'Approved' then 'DONE'
                            when 'Rejected' then 'PENDING'
                            else :reviewStatus
                        end)
                  )
              and (:filterByFrom = false or submission.capturedAt >= :fromInclusive)
              and (:filterByTo = false or submission.capturedAt < :toExclusive)
            order by submission.capturedAt desc, submission.id desc
            """)
    Page<StockCountSubmission> searchByTenant(
            @Param("tenantId") UUID tenantId,
            @Param("filterBySubmittedBy") boolean filterBySubmittedBy,
            @Param("submittedByUserId") UUID submittedByUserId,
            @Param("filterByReviewStatus") boolean filterByReviewStatus,
            @Param("reviewStatus") String reviewStatus,
            @Param("filterByFrom") boolean filterByFrom,
            @Param("fromInclusive") Instant fromInclusive,
            @Param("filterByTo") boolean filterByTo,
            @Param("toExclusive") Instant toExclusive,
            Pageable pageable
    );

    @Query("""
            select case when count(submission) > 0 then true else false end
            from StockCountSubmission submission
            where submission.tenant.id = :tenantId
              and submission.sku.id = :skuId
              and submission.countCycleStartedAt = :countCycleStartedAt
              and submission.reviewStatus <> 'PENDING'
            """)
    boolean existsByTenant_IdAndSku_IdAndCountCycleStartedAt(
            @Param("tenantId") UUID tenantId,
            @Param("skuId") UUID skuId,
            @Param("countCycleStartedAt") Instant countCycleStartedAt
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"tenant", "sku", "submittedBy", "reviewedBy"})
    List<StockCountSubmission> findAllByTenant_IdAndIdIn(UUID tenantId, List<UUID> ids);

    @EntityGraph(attributePaths = {"tenant", "sku", "submittedBy", "reviewedBy"})
    Optional<StockCountSubmission> findByIdAndTenant_Id(UUID id, UUID tenantId);

    @EntityGraph(attributePaths = {"tenant", "sku", "submittedBy", "reviewedBy"})
    @Query("""
            select submission
            from StockCountSubmission submission
            where submission.tenant.id = :tenantId
              and submission.reviewStatus =
                    case :reviewStatus
                        when 'Pending Review' then 'SUBMITTED'
                        when 'Pending' then 'SUBMITTED'
                        when 'Approved' then 'DONE'
                        when 'Rejected' then 'PENDING'
                        else :reviewStatus
                    end
              and submission.capturedAt >= :fromInclusive
              and submission.capturedAt < :toExclusive
            order by submission.capturedAt asc
            """)
    List<StockCountSubmission> findAllByTenant_IdAndReviewStatusAndCapturedAtGreaterThanEqualAndCapturedAtLessThanOrderByCapturedAtAsc(
            @Param("tenantId") UUID tenantId,
            @Param("reviewStatus") String reviewStatus,
            @Param("fromInclusive") Instant fromInclusive,
            @Param("toExclusive") Instant toExclusive
    );

    long countByTenant_IdAndCapturedAtGreaterThanEqualAndCapturedAtLessThan(
            UUID tenantId,
            Instant fromInclusive,
            Instant toExclusive
    );

    @Query("""
            select count(submission)
            from StockCountSubmission submission
            where submission.tenant.id = :tenantId
              and submission.reviewStatus =
                    case :reviewStatus
                        when 'Pending Review' then 'SUBMITTED'
                        when 'Pending' then 'SUBMITTED'
                        when 'Approved' then 'DONE'
                        when 'Rejected' then 'PENDING'
                        else :reviewStatus
                    end
              and submission.capturedAt >= :fromInclusive
              and submission.capturedAt < :toExclusive
            """)
    long countByTenant_IdAndReviewStatusAndCapturedAtGreaterThanEqualAndCapturedAtLessThan(
            @Param("tenantId") UUID tenantId,
            @Param("reviewStatus") String reviewStatus,
            @Param("fromInclusive") Instant fromInclusive,
            @Param("toExclusive") Instant toExclusive
    );
}
