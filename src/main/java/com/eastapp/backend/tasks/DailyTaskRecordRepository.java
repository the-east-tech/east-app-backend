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

public interface DailyTaskRecordRepository extends JpaRepository<DailyTaskRecord, UUID> {
    Optional<DailyTaskRecord> findByIdAndTenantId(UUID id, UUID tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select task
            from DailyTaskRecord task
            where task.id = :id and task.tenantId = :tenantId
            """)
    Optional<DailyTaskRecord> findByIdAndTenantIdForUpdate(
            @Param("id") UUID id,
            @Param("tenantId") UUID tenantId
    );

    Optional<DailyTaskRecord> findByTenantIdAndTemplateIdAndTaskDate(
            UUID tenantId,
            UUID templateId,
            LocalDate taskDate
    );
    List<DailyTaskRecord> findAllByTenantIdAndTaskDateOrderByTagNameAscTitleAsc(
            UUID tenantId,
            LocalDate taskDate
    );
    List<DailyTaskRecord> findAllByTenantIdAndTaskDateBetweenOrderByTaskDateDescTagNameAscTitleAsc(
            UUID tenantId,
            LocalDate dateFrom,
            LocalDate dateTo
    );
    List<DailyTaskRecord> findAllByTenantIdAndSubmittedByUserIdOrderByTaskDateDescSubmittedAtDesc(
            UUID tenantId,
            UUID submittedByUserId
    );
}
