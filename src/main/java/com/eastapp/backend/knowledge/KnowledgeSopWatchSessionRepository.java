package com.eastapp.backend.knowledge;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface KnowledgeSopWatchSessionRepository
        extends JpaRepository<KnowledgeSopWatchSession, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"tenant", "user", "sop"})
    Optional<KnowledgeSopWatchSession> findLockedById(UUID sessionId);

    @Query(value = """
            select
                sop.id as "sopId",
                sop.link_group_id as "linkGroupId",
                sop.title as "title",
                sop.language as "language",
                cast(sum(watch.played_seconds) as bigint) as "totalPlayedSeconds",
                max(case when watch.played_seconds > 0 then watch.last_heartbeat_at end)
                    as "lastWatchedAt"
            from knowledge_sop_watch_sessions watch
            join knowledge_sops sop
              on sop.tenant_id = watch.tenant_id and sop.id = watch.sop_id
            where watch.tenant_id = :tenantId and watch.user_id = :userId
            group by sop.id, sop.link_group_id, sop.title, sop.language
            having sum(watch.played_seconds) > 0
            order by sum(watch.played_seconds) desc, lower(sop.title), sop.language
            """, nativeQuery = true)
    List<UserSopWatchAggregate> aggregateForUser(
            @Param("tenantId") UUID tenantId,
            @Param("userId") UUID userId
    );

    @Query(value = """
            select
                sop.id as "sopId",
                sop.link_group_id as "linkGroupId",
                sop.title as "title",
                sop.language as "language",
                cast(coalesce(sum(watch.played_seconds), 0) as bigint) as "totalPlayedSeconds",
                cast(count(distinct case when watch.played_seconds > 0 then watch.user_id end) as bigint)
                    as "uniqueViewers",
                max(case when watch.played_seconds > 0 then watch.last_heartbeat_at end)
                    as "lastWatchedAt"
            from knowledge_sops sop
            left join knowledge_sop_watch_sessions watch
              on watch.tenant_id = sop.tenant_id and watch.sop_id = sop.id
            where sop.tenant_id = :tenantId
            group by sop.id, sop.link_group_id, sop.title, sop.language
            order by coalesce(sum(watch.played_seconds), 0) desc, lower(sop.title), sop.language
            """, nativeQuery = true)
    List<SopWatchImpactAggregate> aggregateImpact(@Param("tenantId") UUID tenantId);

    @Query("""
            select count(distinct session.user.id)
            from KnowledgeSopWatchSession session
            where session.tenant.id = :tenantId and session.playedSeconds > 0
            """)
    long countDistinctViewers(@Param("tenantId") UUID tenantId);
}
