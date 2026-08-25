package com.eastapp.backend.tasks;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DailyTaskTemplateRepository extends JpaRepository<DailyTaskTemplate, UUID> {
    List<DailyTaskTemplate> findAllByTenantIdOrderByActiveDescTitleAsc(UUID tenantId);
    List<DailyTaskTemplate> findAllByTenantIdAndActiveTrueOrderByTitleAsc(UUID tenantId);
    Optional<DailyTaskTemplate> findByIdAndTenantId(UUID id, UUID tenantId);
    boolean existsByTenantIdAndTagId(UUID tenantId, UUID tagId);
}
