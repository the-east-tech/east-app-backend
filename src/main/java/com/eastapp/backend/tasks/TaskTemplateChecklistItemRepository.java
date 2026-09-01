package com.eastapp.backend.tasks;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TaskTemplateChecklistItemRepository
        extends JpaRepository<TaskTemplateChecklistItem, UUID> {
    List<TaskTemplateChecklistItem> findAllByTenantIdAndTemplateIdOrderByPositionAsc(
            UUID tenantId,
            UUID templateId
    );
    List<TaskTemplateChecklistItem> findAllByTenantIdAndTemplateIdIn(
            UUID tenantId,
            Collection<UUID> templateIds
    );
    void deleteAllByTenantIdAndTemplateId(UUID tenantId, UUID templateId);
}
