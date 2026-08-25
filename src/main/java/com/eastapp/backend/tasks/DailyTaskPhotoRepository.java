package com.eastapp.backend.tasks;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface DailyTaskPhotoRepository extends JpaRepository<DailyTaskPhoto, UUID> {
    List<DailyTaskPhoto> findAllByTenantIdAndRecordIdOrderBySubmittedAtAscIdAsc(
            UUID tenantId,
            UUID recordId
    );
    List<DailyTaskPhoto> findAllByTenantIdAndRecordIdIn(
            UUID tenantId,
            Collection<UUID> recordIds
    );
}
