package com.eastapp.backend.reports;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WasteReportDetailRepository extends JpaRepository<WasteReportDetail, UUID> {
    Optional<WasteReportDetail> findByReportIdAndTenantId(UUID reportId, UUID tenantId);
    List<WasteReportDetail> findAllByTenantIdAndReportIdIn(UUID tenantId, List<UUID> reportIds);
}
