package com.eastapp.backend.reports;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReportMediaRepository extends JpaRepository<ReportMedia, UUID> {
    Optional<ReportMedia> findByTenantIdAndStorageKey(UUID tenantId, String storageKey);
    Optional<ReportMedia> findByIdAndTenantId(UUID id, UUID tenantId);
    List<ReportMedia> findAllByTenantIdAndIdIn(UUID tenantId, List<UUID> ids);

    // Avoid fetching the potentially multi-megabyte content_bytes column when
    // a response only needs a media URL key.
    @Query("""
            select new com.eastapp.backend.reports.ReportMediaReference(
                media.id,
                media.storageKey
            )
            from ReportMedia media
            where media.tenantId = :tenantId and media.id in :ids
            """)
    List<ReportMediaReference> findAllReferencesByTenantIdAndIdIn(
            @Param("tenantId") UUID tenantId,
            @Param("ids") List<UUID> ids
    );
}
