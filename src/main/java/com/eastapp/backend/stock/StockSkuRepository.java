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

public interface StockSkuRepository extends JpaRepository<StockSku, UUID> {

    @EntityGraph(attributePaths = {
            "tenant", "lastUpdatedBy", "createdBy", "suppliers",
            "tag1", "tag2", "thumbnailMedia"
    })
    List<StockSku> findAllByTenant_IdOrderByNameAsc(UUID tenantId);

    @EntityGraph(attributePaths = {
            "tenant", "lastUpdatedBy", "createdBy", "suppliers",
            "tag1", "tag2", "thumbnailMedia"
    })
    @Query("""
            select sku
            from StockSku sku
            where sku.tenant.id = :tenantId
              and (:active is null or sku.active = :active)
              and (
                    :search = ''
                    or lower(sku.name) like lower(concat('%', :search, '%'))
                    or lower(sku.tag1.tag) like lower(concat('%', :search, '%'))
                    or lower(sku.tag2.tag) like lower(concat('%', :search, '%'))
                    or lower(sku.unit) like lower(concat('%', :search, '%'))
                    or exists (
                        select 1
                        from StockSku searchedSku
                        join searchedSku.suppliers supplier
                        where searchedSku = sku
                          and lower(supplier.supplierName) like lower(concat('%', :search, '%'))
                    )
                  )
            order by lower(sku.name), sku.id
            """)
    Page<StockSku> searchByTenant(
            @Param("tenantId") UUID tenantId,
            @Param("search") String search,
            @Param("active") Boolean active,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {
            "tenant", "lastUpdatedBy", "createdBy", "suppliers",
            "tag1", "tag2", "thumbnailMedia"
    })
    Optional<StockSku> findByIdAndTenant_Id(UUID id, UUID tenantId);

    @EntityGraph(attributePaths = {
            "tenant", "lastUpdatedBy", "createdBy", "suppliers",
            "tag1", "tag2", "thumbnailMedia"
    })
    List<StockSku> findAllByTenant_IdAndIdIn(UUID tenantId, List<UUID> ids);

    boolean existsByTenant_IdAndNameIgnoreCase(UUID tenantId, String name);

    boolean existsByTenant_IdAndSuppliers_Id(UUID tenantId, UUID supplierId);

    boolean existsByTenant_IdAndTag1_Id(UUID tenantId, UUID tagId);

    boolean existsByTenant_IdAndTag2_Id(UUID tenantId, UUID tagId);
}
