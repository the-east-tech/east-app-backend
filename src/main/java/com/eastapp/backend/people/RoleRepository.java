package com.eastapp.backend.people;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoleRepository extends JpaRepository<Role, UUID> {

    List<Role> findAllByTenant_IdOrderByNameAsc(UUID tenantId);

    @EntityGraph(attributePaths = "tenant")
    Optional<Role> findByIdAndTenant_Id(UUID id, UUID tenantId);

    Optional<Role> findByTenant_IdAndSystemKey(UUID tenantId, SystemRole systemKey);

    boolean existsByTenant_IdAndNameIgnoreCase(UUID tenantId, String name);

    boolean existsByTenant_IdAndNameIgnoreCaseAndIdNot(UUID tenantId, String name, UUID id);
}
