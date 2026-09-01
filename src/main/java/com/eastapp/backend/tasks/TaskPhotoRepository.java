package com.eastapp.backend.tasks;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TaskPhotoRepository extends JpaRepository<TaskPhoto, UUID> {
    List<TaskPhoto> findAllByTenantIdAndRecordIdOrderBySubmittedAtAscIdAsc(
            UUID tenantId,
            UUID recordId
    );
    List<TaskPhoto> findAllByTenantIdAndRecordIdIn(
            UUID tenantId,
            Collection<UUID> recordIds
    );
}
