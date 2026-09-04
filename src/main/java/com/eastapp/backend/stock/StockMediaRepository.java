package com.eastapp.backend.stock;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StockMediaRepository extends JpaRepository<StockMedia, UUID> {
    Optional<StockMedia> findByTenant_IdAndStorageKey(UUID tenantId, String storageKey);

    Optional<StockMediaReference> findReferenceByTenantIdAndStorageKey(
            UUID tenantId,
            String storageKey
    );

    List<StockMediaReference> findAllReferencesByTenantIdAndIdIn(
            UUID tenantId,
            Collection<UUID> ids
    );
}
