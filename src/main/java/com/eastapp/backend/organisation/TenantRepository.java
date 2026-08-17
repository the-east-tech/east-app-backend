package com.eastapp.backend.organisation;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    boolean existsByCompanyCode(String companyCode);

    boolean existsByEmployeeIdPrefix(String employeeIdPrefix);

    List<Tenant> findAllByActiveTrueOrderByBusinessNameAsc();

    List<Tenant> findAllByOrderByBusinessNameAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select tenant from Tenant tenant where tenant.id = :tenantId")
    Optional<Tenant> findByIdForUpdate(@Param("tenantId") UUID tenantId);
}
