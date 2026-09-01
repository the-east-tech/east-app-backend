package com.eastapp.backend.tasks;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TaskAuditEntryRepository extends JpaRepository<TaskAuditEntry, UUID> {
    List<TaskAuditEntry> findAllByTenantIdAndTemplateIdAndRecordIdIsNullOrderByOccurredAtAscIdAsc(
            UUID tenantId,
            UUID templateId
    );

    List<TaskAuditEntry> findAllByTenantIdAndRecordIdOrderByOccurredAtAscIdAsc(
            UUID tenantId,
            UUID recordId
    );

    List<TaskAuditEntry> findAllByTenantIdAndRecordIdIn(
            UUID tenantId,
            Collection<UUID> recordIds
    );
}
