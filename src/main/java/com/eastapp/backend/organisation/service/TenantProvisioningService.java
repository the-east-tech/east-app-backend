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
                new Role(tenant, SystemRole.HEAD, "Head"),
                new Role(tenant, SystemRole.MANAGER, "Manager"),
                new Role(tenant, SystemRole.SUPERVISOR, "Supervisor"),
                new Role(tenant, SystemRole.STAFF_1, "Staff1"),
                new Role(tenant, SystemRole.STAFF_2, "Staff2")
        );
        roleRepository.saveAll(roles);
        Role headRole = roles.getFirst();

        String employeeId = tenant.allocateEmployeeId();
        UserAccount owner = new UserAccount(
                tenant,
                ownerIdentity,
                employeeId,
                ownerFullName,
                ownerPhoneE164,
                headRole
        );
        owner.updateProfile(
                ownerFullName,
                ownerPhoneE164,
                profilePhotoKey,
                birthDate,
                startDate,
                endDate
        );
        userAccountRepository.saveAndFlush(owner);

        return new ProvisionedTenant(tenant, owner);
    }

    public record ProvisionedTenant(Tenant tenant, UserAccount owner) {
    }
}
