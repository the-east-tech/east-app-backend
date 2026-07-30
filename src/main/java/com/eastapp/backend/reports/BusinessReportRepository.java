package com.eastapp.backend.reports;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BusinessReportRepository extends JpaRepository<BusinessReport, UUID> {
    Optional<BusinessReport> findByIdAndTenantId(UUID id, UUID tenantId);

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
}
