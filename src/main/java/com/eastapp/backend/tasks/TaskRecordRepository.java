package com.eastapp.backend.tasks;

import com.eastapp.backend.people.SystemRole;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskRecordRepository extends JpaRepository<TaskRecord, UUID> {
    Optional<TaskRecord> findByIdAndTenantId(UUID id, UUID tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<TaskRecord> findLockedByIdAndTenantId(
            UUID id,
            UUID tenantId
    );

    Optional<TaskRecord> findByTenantIdAndTemplateIdAndTaskDate(
            UUID tenantId,
            UUID templateId,
            LocalDate taskDate
    );
    List<TaskRecord> findAllByTenantIdAndTaskDateOrderByTagNameAscTitleAsc(
            UUID tenantId,
            LocalDate taskDate
    );
    List<TaskRecord> findAllByTenantIdAndTaskDateBetweenOrderByTaskDateDescTagNameAscTitleAsc(
            UUID tenantId,
            LocalDate dateFrom,
            LocalDate dateTo
    );
    List<TaskRecord> findAllByTenantIdAndTaskDateBetweenAndStatusInOrderByTaskDateDescTagNameAscTitleAsc(
            UUID tenantId,
            LocalDate dateFrom,
            LocalDate dateTo,
            Collection<TaskStatus> statuses
    );
    List<TaskRecord> findAllByTenantIdAndTaskDateAndStatusOrderByTagNameAscTitleAsc(
            UUID tenantId,
            LocalDate taskDate,
            TaskStatus status
    );
    List<TaskRecord> findAllByTenantIdAndSubmittedByUserIdOrderByTaskDateDescSubmittedAtDesc(
            UUID tenantId,
            UUID submittedByUserId
    );

    List<TaskRecord> findAllByTenantIdAndStatusAndSubmittedByRoleInOrderBySubmittedAtAsc(
            UUID tenantId,
            TaskStatus status,
            Collection<SystemRole> submittedByRoles
    );

    long countByTenantIdAndStatusAndSubmittedByRoleIn(
            UUID tenantId,
            TaskStatus status,
            Collection<SystemRole> submittedByRoles
    );
}
