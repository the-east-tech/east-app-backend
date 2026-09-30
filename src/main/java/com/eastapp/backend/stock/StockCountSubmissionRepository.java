package com.eastapp.backend.stock;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.time.Instant;
import java.util.Collection;
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
                    (:filterByWorkflowStatus = false and submission.workflowStatus <> :rejectedStatus)
                    or (:filterByWorkflowStatus = true and submission.workflowStatus = :workflowStatus)
                  )
            order by submission.capturedAt desc, submission.id desc
            """)
    Page<StockCountSubmission> searchByTenant(
            @Param("tenantId") UUID tenantId,
            @Param("filterBySubmittedBy") boolean filterBySubmittedBy,
            @Param("submittedByUserId") UUID submittedByUserId,
            @Param("filterByWorkflowStatus") boolean filterByWorkflowStatus,
            @Param("workflowStatus") StockWorkflowStatus workflowStatus,
            @Param("rejectedStatus") StockWorkflowStatus rejectedStatus,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select submission from StockCountSubmission submission where submission.tenant.id = :tenantId and submission.sku.id in :skuIds")
    List<StockCountSubmission> findAllLockedByTenantIdAndSkuIds(
            @Param("tenantId") UUID tenantId, @Param("skuIds") Collection<UUID> skuIds
    );

    @Modifying
    @Query(value = """
            INSERT INTO stock_count_approved_days (submission_id, count_date)
            VALUES (:submissionId, :countDate)
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    void recordApprovedDay(
            @Param("submissionId") UUID submissionId,
            @Param("countDate") java.time.LocalDate countDate
    );

    @Query(value = """
            SELECT submission.sku_id
            FROM stock_count_approved_days days
            JOIN stock_count_submissions submission ON submission.id = days.submission_id
            WHERE submission.tenant_id = :tenantId
              AND days.count_date BETWEEN :fromDate AND :toDate
            """, nativeQuery = true)
    List<UUID> approvedSkuIdsForDays(
            @Param("tenantId") UUID tenantId,
            @Param("fromDate") java.time.LocalDate fromDate,
            @Param("toDate") java.time.LocalDate toDate
    );

    boolean existsByTenant_IdAndSku_Id(UUID tenantId, UUID skuId);

    @Query("""
            select distinct submission.sku.id from StockCountSubmission submission
            where submission.tenant.id = :tenantId and submission.workflowStatus = :status
              and submission.sku.id in :skuIds
            """)
    List<UUID> submittedSkuIds(
            @Param("tenantId") UUID tenantId,
            @Param("status") StockWorkflowStatus status,
            @Param("skuIds") Collection<UUID> skuIds
    );

    boolean existsByTenant_IdAndSku_IdAndWorkflowStatus(
            UUID tenantId, UUID skuId, StockWorkflowStatus workflowStatus
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"tenant", "sku", "submittedBy", "reviewedBy"})
    List<StockCountSubmission> findAllByTenant_IdAndIdIn(UUID tenantId, List<UUID> ids);

    @EntityGraph(attributePaths = {"tenant", "sku", "submittedBy", "reviewedBy"})
    Optional<StockCountSubmission> findByIdAndTenant_Id(UUID id, UUID tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select submission from StockCountSubmission submission where submission.id = :id and submission.tenant.id = :tenantId")
    Optional<StockCountSubmission> findLockedByIdAndTenantId(
            @Param("id") UUID id, @Param("tenantId") UUID tenantId
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
