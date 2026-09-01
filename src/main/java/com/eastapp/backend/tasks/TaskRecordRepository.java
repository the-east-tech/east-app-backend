package com.eastapp.backend.tasks;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskRecordRepository extends JpaRepository<TaskRecord, UUID> {
    Optional<TaskRecord> findByIdAndTenantId(UUID id, UUID tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select task
            from TaskRecord task
            where task.id = :id and task.tenantId = :tenantId
            """)
    Optional<TaskRecord> findByIdAndTenantIdForUpdate(
            @Param("id") UUID id,
            @Param("tenantId") UUID tenantId
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
    List<TaskRecord> findAllByTenantIdAndSubmittedByUserIdOrderByTaskDateDescSubmittedAtDesc(
            UUID tenantId,
            UUID submittedByUserId
    );
}
