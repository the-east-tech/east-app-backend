package com.eastapp.backend.tasks;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DailyTaskAuditEntryRepository extends JpaRepository<DailyTaskAuditEntry, UUID> {
    List<DailyTaskAuditEntry> findAllByTenantIdAndTemplateIdAndRecordIdIsNullOrderByOccurredAtAscIdAsc(
            UUID tenantId,
            UUID templateId
    );

    List<DailyTaskAuditEntry> findAllByTenantIdAndRecordIdOrderByOccurredAtAscIdAsc(
            UUID tenantId,
            UUID recordId
    );
}
