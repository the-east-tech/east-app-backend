package com.eastapp.backend.reports;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ComplaintReportDetailRepository extends JpaRepository<ComplaintReportDetail, UUID> {
    Optional<ComplaintReportDetail> findByReportIdAndTenantId(UUID reportId, UUID tenantId);
    List<ComplaintReportDetail> findAllByTenantIdAndReportIdIn(UUID tenantId, List<UUID> reportIds);
    long countByTenantIdAndComplaintStatus(UUID tenantId, ComplaintStatus complaintStatus);
}
