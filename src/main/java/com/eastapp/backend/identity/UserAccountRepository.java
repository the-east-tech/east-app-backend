package com.eastapp.backend.identity;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

    @EntityGraph(attributePaths = {"tenant", "role"})
    List<UserAccount> findAllByTenant_IdOrderByFullNameAsc(UUID tenantId);

    @EntityGraph(attributePaths = {"tenant", "role"})
    Optional<UserAccount> findByIdAndTenant_Id(UUID id, UUID tenantId);

    @EntityGraph(attributePaths = {"tenant", "role"})
    Optional<UserAccount> findByTenant_CompanyCodeAndEmployeeIdAndPhoneE164(
            String companyCode,
            String employeeId,
            String phoneE164
    );

    boolean existsByTenant_IdAndEmployeeId(UUID tenantId, String employeeId);

    long countByRole_Id(UUID roleId);

    long countByTenant_IdAndRole_SystemKeyAndActiveTrue(UUID tenantId, SystemRole systemKey);
}
