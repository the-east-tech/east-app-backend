package com.eastapp.backend.stock;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StockTagRepository extends JpaRepository<StockTag, UUID> {
    @EntityGraph(attributePaths = {"tenant", "createdBy", "updatedBy"})
    List<StockTag> findAllByTenant_IdOrderByTagAsc(UUID tenantId);

    @EntityGraph(attributePaths = {"tenant", "createdBy", "updatedBy"})
    @Query("""
            select tag
            from StockTag tag
            where tag.tenant.id = :tenantId
              and (:search = '' or lower(tag.tag) like lower(concat('%', :search, '%')))
            order by lower(tag.tag), tag.id
            """)
    Page<StockTag> searchByTenant(
            @Param("tenantId") UUID tenantId,
            @Param("search") String search,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"tenant", "createdBy", "updatedBy"})
    Optional<StockTag> findByIdAndTenant_Id(UUID id, UUID tenantId);

    boolean existsByTenant_IdAndTagIgnoreCase(UUID tenantId, String tag);
}
