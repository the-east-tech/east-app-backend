package com.eastapp.backend.knowledge;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface KnowledgeSopRepository extends JpaRepository<KnowledgeSop, UUID> {
    @EntityGraph(attributePaths = {"tenant", "tag", "createdBy"})
    @Query("""
            select sop
            from KnowledgeSop sop
            where sop.tenant.id = :tenantId
              and (:filterByTag = false or sop.tag.id = :tagId)
              and (
                    :search = ''
                    or lower(sop.title) like lower(concat('%', :search, '%'))
                    or lower(sop.expectedOutcome) like lower(concat('%', :search, '%'))
                    or lower(sop.description) like lower(concat('%', :search, '%'))
                    or lower(sop.tag.tag) like lower(concat('%', :search, '%'))
              )
            order by sop.createdAt desc, sop.id desc
            """)
    Page<KnowledgeSop> searchByTenant(
            @Param("tenantId") UUID tenantId,
            @Param("filterByTag") boolean filterByTag,
            @Param("tagId") UUID tagId,
            @Param("search") String search,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"tenant", "tag", "createdBy"})
    Optional<KnowledgeSop> findByIdAndTenant_Id(UUID id, UUID tenantId);

    boolean existsByTenant_IdAndTag_Id(UUID tenantId, UUID tagId);
}
