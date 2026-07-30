package com.eastapp.backend.reports;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DailyReportPhotoRepository extends JpaRepository<DailyReportPhoto, UUID> {
    List<DailyReportPhoto> findAllByTenantIdAndReportIdOrderByCreatedAtAsc(UUID tenantId, UUID reportId);
    long countByTenantIdAndReportId(UUID tenantId, UUID reportId);
}
