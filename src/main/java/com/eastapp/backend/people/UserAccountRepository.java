package com.eastapp.backend.people;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

    @EntityGraph(attributePaths = {"identity", "tenant", "role"})
    List<UserAccount> findAllByTenant_IdOrderByIdentity_FullNameAsc(UUID tenantId);

    @EntityGraph(attributePaths = {"identity", "tenant", "role"})
    List<UserAccount> findAllByTenant_IdAndActiveTrueOrderByIdentity_FullNameAsc(UUID tenantId);

    @EntityGraph(attributePaths = {"identity", "tenant", "role"})
    @Query("""
            select user
            from UserAccount user
            where user.tenant.id = :tenantId
              and user.role.systemKey in :visibleRoles
              and (:role is null or user.role.systemKey = :role)
              and (:active is null or user.active = :active)
              and (
                    :search = ''
                    or lower(user.employeeId) like lower(concat('%', :search, '%'))
                    or lower(user.identity.fullName) like lower(concat('%', :search, '%'))
                    or lower(user.identity.phoneE164) like lower(concat('%', :search, '%'))
                    or lower(user.role.name) like lower(concat('%', :search, '%'))
              )
            """)
    Page<UserAccount> searchByTenant(
            @Param("tenantId") UUID tenantId,
            @Param("search") String search,
            @Param("active") Boolean active,
            @Param("role") SystemRole role,
            @Param("visibleRoles") Collection<SystemRole> visibleRoles,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"identity", "tenant", "role"})
    Optional<UserAccount> findByIdAndTenant_Id(UUID id, UUID tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"identity", "tenant", "role"})
    @Query("select user from UserAccount user where user.id = :id and user.tenant.id = :tenantId")
    Optional<UserAccount> findByIdAndTenant_IdForUpdate(
            @Param("id") UUID id,
            @Param("tenantId") UUID tenantId
    );

    @EntityGraph(attributePaths = {"identity", "tenant", "role"})
    Optional<UserAccount> findByTenant_CompanyCodeAndEmployeeIdAndIdentity_PhoneE164(
            String companyCode,
            String employeeId,
            String phoneE164
    );

    @EntityGraph(attributePaths = {"identity", "tenant", "role"})
    @Query("""
            select user
            from UserAccount user
            where user.identity.id = :identityId
            order by lower(user.tenant.businessName), lower(user.identity.fullName), user.id
            """)
    List<UserAccount> findAllContexts(@Param("identityId") UUID identityId);

    @EntityGraph(attributePaths = {"identity", "tenant", "role"})
    Optional<UserAccount> findByIdAndIdentity_Id(UUID id, UUID identityId);

    @EntityGraph(attributePaths = {"identity", "tenant", "role"})
    Optional<UserAccount> findByTenant_IdAndIdentity_Id(UUID tenantId, UUID identityId);

    @EntityGraph(attributePaths = {"identity", "tenant", "role"})
    Optional<UserAccount> findByTenant_IdAndEmployeeId(UUID tenantId, String employeeId);

    boolean existsByIdentity_IdAndTenant_Id(UUID identityId, UUID tenantId);
    boolean existsByTenant_IdAndEmployeeId(UUID tenantId, String employeeId);
    long countByRole_Id(UUID roleId);

    @Query("""
            select user.role.id, count(user.id)
            from UserAccount user
            where user.tenant.id = :tenantId
            group by user.role.id
            """)
    List<Object[]> countUsersByRoleForTenant(@Param("tenantId") UUID tenantId);

    @EntityGraph(attributePaths = {"identity", "tenant", "role"})
    List<UserAccount> findAllByRole_SystemKeyAndActiveTrueOrderByCreatedAtAsc(SystemRole systemKey);

    long countByTenant_IdAndRole_SystemKeyAndActiveTrue(UUID tenantId, SystemRole systemKey);
}
