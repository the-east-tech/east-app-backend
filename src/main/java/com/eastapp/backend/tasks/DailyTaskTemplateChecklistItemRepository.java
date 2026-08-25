package com.eastapp.backend.tasks;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface DailyTaskTemplateChecklistItemRepository
        extends JpaRepository<DailyTaskTemplateChecklistItem, UUID> {
    List<DailyTaskTemplateChecklistItem> findAllByTenantIdAndTemplateIdOrderByPositionAsc(
            UUID tenantId,
            UUID templateId
    );
    List<DailyTaskTemplateChecklistItem> findAllByTenantIdAndTemplateIdIn(
            UUID tenantId,
            Collection<UUID> templateIds
    );
    void deleteAllByTenantIdAndTemplateId(UUID tenantId, UUID templateId);
}
