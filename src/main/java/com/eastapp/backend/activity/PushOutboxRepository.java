package com.eastapp.backend.activity;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface PushOutboxRepository extends JpaRepository<PushOutbox, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"notification", "notification.activityEvent", "device"})
    @Query("""
            select outbox
            from PushOutbox outbox
            where outbox.sentAt is null
              and outbox.nextAttemptAt <= :now
              and outbox.expiresAt > :now
            order by outbox.createdAt asc, outbox.id asc
            """)
    List<PushOutbox> findDue(@Param("now") Instant now, Pageable pageable);
}
