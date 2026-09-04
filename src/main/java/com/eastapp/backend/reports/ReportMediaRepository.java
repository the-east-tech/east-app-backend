package com.eastapp.backend.reports;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReportMediaRepository extends JpaRepository<ReportMedia, UUID> {
    Optional<ReportMedia> findByTenantIdAndStorageKey(UUID tenantId, String storageKey);

    // Avoid fetching the potentially multi-megabyte content_bytes column when
    // a response only needs a media URL key.
    List<ReportMediaReference> findAllReferencesByTenantIdAndIdIn(
            UUID tenantId,
            List<UUID> ids
    );

    Optional<ReportMediaReference> findReferenceByIdAndTenantId(
            UUID id,
            UUID tenantId
    );

    Optional<ReportMediaReference> findReferenceByTenantIdAndStorageKey(
            UUID tenantId,
            String storageKey
    );
}
