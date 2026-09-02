package com.eastapp.backend.reports;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BusinessReportRepository extends JpaRepository<BusinessReport, UUID> {
    Optional<BusinessReport> findByIdAndTenantId(UUID id, UUID tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select report from BusinessReport report where report.id = :id and report.tenantId = :tenantId")
    Optional<BusinessReport> findByIdAndTenantIdForUpdate(
            @Param("id") UUID id,
            @Param("tenantId") UUID tenantId
    );

    Optional<BusinessReport> findByTenantIdAndReportTypeAndReportDate(
            UUID tenantId,
            BusinessReportType reportType,
            LocalDate reportDate
    );

    Optional<BusinessReport> findByTenantIdAndReportTypeAndReportDateAndSubmittedByUserId(
            UUID tenantId,
            BusinessReportType reportType,
            LocalDate reportDate,
            UUID submittedByUserId
    );

    List<BusinessReport> findAllByTenantIdAndReportTypeAndReportDateBetweenOrderByReportDateAscCreatedAtAsc(
            UUID tenantId,
            BusinessReportType reportType,
            LocalDate from,
            LocalDate to
    );

    List<BusinessReport> findAllByTenantIdAndWorkflowStatusOrderBySubmittedAtAsc(
            UUID tenantId,
            ReportWorkflowStatus workflowStatus
    );

    List<BusinessReport> findAllByTenantIdAndReportTypeAndWorkflowStatusOrderBySubmittedAtAsc(
            UUID tenantId,
            BusinessReportType reportType,
            ReportWorkflowStatus workflowStatus
    );

    long countByTenantIdAndWorkflowStatus(
            UUID tenantId,
            ReportWorkflowStatus workflowStatus
    );

    long countByTenantIdAndReportTypeAndWorkflowStatus(
            UUID tenantId,
            BusinessReportType reportType,
            ReportWorkflowStatus workflowStatus
    );
}
