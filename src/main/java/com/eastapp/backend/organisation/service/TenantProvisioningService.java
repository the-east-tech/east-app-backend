package com.eastapp.backend.organisation.service;

import com.eastapp.backend.auth.LoginIdentity;
import com.eastapp.backend.organisation.Tenant;
import com.eastapp.backend.organisation.TenantRepository;
import com.eastapp.backend.people.Role;
import com.eastapp.backend.people.RoleRepository;
import com.eastapp.backend.people.SystemRole;
import com.eastapp.backend.people.UserAccount;
import com.eastapp.backend.people.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class TenantProvisioningService {

    private final TenantRepository tenantRepository;
    private final RoleRepository roleRepository;
    private final UserAccountRepository userAccountRepository;

    public TenantProvisioningService(
            TenantRepository tenantRepository,
            RoleRepository roleRepository,
            UserAccountRepository userAccountRepository
    ) {
        this.tenantRepository = tenantRepository;
        this.roleRepository = roleRepository;
        this.userAccountRepository = userAccountRepository;
    }

    @Transactional
    public ProvisionedTenant provision(
            String companyCode,
            String businessName,
            String employeeIdPrefix,
            LoginIdentity ownerIdentity,
            String ownerFullName,
            String ownerPhoneE164,
            String profilePhotoKey,
            LocalDate birthDate,
            LocalDate startDate,
            LocalDate endDate
    ) {
        Tenant tenant = tenantRepository.save(
                new Tenant(companyCode, businessName, employeeIdPrefix)
        );

        List<Role> roles = List.of(
                new Role(tenant, SystemRole.OWNER, "Owner"),
                new Role(tenant, SystemRole.HEAD, "Head"),
                new Role(tenant, SystemRole.MANAGER, "Manager"),
                new Role(tenant, SystemRole.SUPERVISOR, "Supervisor"),
                new Role(tenant, SystemRole.STAFF_1, "Staff1"),
                new Role(tenant, SystemRole.STAFF_2, "Staff2")
        );
        roleRepository.saveAll(roles);
        Role ownerRole = roles.getFirst();

        UserAccount owner = createOwnerContext(
                tenant,
                ownerRole,
                ownerIdentity,
                ownerFullName,
                ownerPhoneE164,
                profilePhotoKey,
                birthDate,
                startDate,
                endDate
        );
        userAccountRepository.saveAndFlush(owner);

        return new ProvisionedTenant(tenant, ownerRole, owner);
    }

    @Transactional
    public UserAccount addOwnerContext(
            ProvisionedTenant provisioned,
            UserAccount sourceOwner
    ) {
        if (userAccountRepository.existsByIdentity_IdAndTenant_Id(
                sourceOwner.getIdentity().getId(),
                provisioned.tenant().getId()
        )) {
            return userAccountRepository.findByTenant_IdAndIdentity_Id(
                    provisioned.tenant().getId(),
                    sourceOwner.getIdentity().getId()
            ).orElseThrow();
        }

        UserAccount owner = createOwnerContext(
                provisioned.tenant(),
                provisioned.ownerRole(),
                sourceOwner.getIdentity(),
                sourceOwner.getFullName(),
                sourceOwner.getPhoneE164(),
                sourceOwner.getProfilePhotoKey(),
                sourceOwner.getBirthDate(),
                sourceOwner.getStartDate(),
                sourceOwner.getEndDate()
        );
        return userAccountRepository.save(owner);
    }

    private static UserAccount createOwnerContext(
            Tenant tenant,
            Role ownerRole,
            LoginIdentity identity,
            String fullName,
            String phoneE164,
            String profilePhotoKey,
            LocalDate birthDate,
            LocalDate startDate,
            LocalDate endDate
    ) {
        UserAccount owner = new UserAccount(
                tenant,
                identity,
                tenant.allocateEmployeeId(),
                fullName,
                phoneE164,
                ownerRole
        );
        owner.updateProfile(
                fullName,
                phoneE164,
                profilePhotoKey,
                birthDate,
                startDate,
                endDate
        );
        return owner;
    }

    public record ProvisionedTenant(Tenant tenant, Role ownerRole, UserAccount owner) {
    }
}
