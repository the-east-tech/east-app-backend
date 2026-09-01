package com.eastapp.backend.tasks;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskTemplateRepository extends JpaRepository<TaskTemplate, UUID> {
    List<TaskTemplate> findAllByTenantIdOrderByActiveDescTitleAsc(UUID tenantId);
    List<TaskTemplate> findAllByTenantIdAndActiveTrueOrderByTitleAsc(UUID tenantId);
    Optional<TaskTemplate> findByIdAndTenantId(UUID id, UUID tenantId);
    boolean existsByTenantIdAndTagId(UUID tenantId, UUID tagId);
}
