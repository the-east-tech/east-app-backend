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

public interface StockSupplierRepository extends JpaRepository<StockSupplier, UUID> {
    @EntityGraph(attributePaths = {"tenant", "lastBalanceUpdatedBy", "createdBy"})
    List<StockSupplier> findAllByTenant_IdOrderBySupplierNameAsc(UUID tenantId);

    @EntityGraph(attributePaths = {"tenant", "lastBalanceUpdatedBy", "createdBy"})
    @Query("""
            select supplier
            from StockSupplier supplier
            where supplier.tenant.id = :tenantId
              and (
                    :search = ''
                    or lower(supplier.supplierName) like lower(concat('%', :search, '%'))
                    or lower(supplier.supplierItem) like lower(concat('%', :search, '%'))
                    or lower(supplier.contactPerson) like lower(concat('%', :search, '%'))
                  )
            order by lower(supplier.supplierName), supplier.id
            """)
    Page<StockSupplier> searchByTenant(
            @Param("tenantId") UUID tenantId,
            @Param("search") String search,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"tenant", "lastBalanceUpdatedBy", "createdBy"})
    Optional<StockSupplier> findByIdAndTenant_Id(UUID id, UUID tenantId);

    boolean existsByTenant_IdAndSupplierNameIgnoreCase(UUID tenantId, String supplierName);
}
