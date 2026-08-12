package com.eastapp.backend.reports;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SalesReportDetailRepository extends JpaRepository<SalesReportDetail, UUID> {
    Optional<SalesReportDetail> findByReportIdAndTenantId(UUID reportId, UUID tenantId);
    List<SalesReportDetail> findAllByTenantIdAndReportIdIn(UUID tenantId, List<UUID> reportIds);
}
