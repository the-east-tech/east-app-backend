package com.eastapp.backend.tasks;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TaskRecordChecklistItemRepository
        extends JpaRepository<TaskRecordChecklistItem, UUID> {
    List<TaskRecordChecklistItem> findAllByTenantIdAndRecordIdOrderByPositionAsc(
            UUID tenantId,
            UUID recordId
    );
    List<TaskRecordChecklistItem> findAllByTenantIdAndRecordIdIn(
            UUID tenantId,
            Collection<UUID> recordIds
    );
}
