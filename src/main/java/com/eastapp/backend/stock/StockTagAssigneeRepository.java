package com.eastapp.backend.stock;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface StockTagAssigneeRepository extends JpaRepository<StockTagAssignee, UUID> {
    List<StockTagAssignee> findAllByTenantIdAndTagIdOrderByCreatedAtAsc(UUID tenantId, UUID tagId);
    List<StockTagAssignee> findAllByTenantIdAndTagIdIn(UUID tenantId, Collection<UUID> tagIds);
    List<StockTagAssignee> findAllByTenantIdAndUserId(UUID tenantId, UUID userId);
    boolean existsByTenantIdAndTagIdAndUserId(UUID tenantId, UUID tagId, UUID userId);
}
