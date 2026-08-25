package com.eastapp.backend.tasks;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface DailyTaskRecordChecklistItemRepository
        extends JpaRepository<DailyTaskRecordChecklistItem, UUID> {
    List<DailyTaskRecordChecklistItem> findAllByTenantIdAndRecordIdOrderByPositionAsc(
            UUID tenantId,
            UUID recordId
    );
    List<DailyTaskRecordChecklistItem> findAllByTenantIdAndRecordIdIn(
            UUID tenantId,
            Collection<UUID> recordIds
    );
}
