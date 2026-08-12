package com.eastapp.backend.reports;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReportMediaRepository extends JpaRepository<ReportMedia, UUID> {
    Optional<ReportMedia> findByTenantIdAndStorageKey(UUID tenantId, String storageKey);
    Optional<ReportMedia> findByIdAndTenantId(UUID id, UUID tenantId);
    List<ReportMedia> findAllByTenantIdAndIdIn(UUID tenantId, List<UUID> ids);
}
