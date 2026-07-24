package com.eastapp.backend.identity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    Optional<Tenant> findByCompanyCode(String companyCode);

    boolean existsByCompanyCode(String companyCode);
}
