package com.eastapp.backend.organisation.service;

import com.eastapp.backend.auth.LoginIdentity;
import com.eastapp.backend.organisation.Tenant;
import com.eastapp.backend.organisation.TenantRepository;
import com.eastapp.backend.people.Role;
import com.eastapp.backend.people.RoleRepository;
import com.eastapp.backend.people.SystemRole;
import com.eastapp.backend.people.UserAccount;
import com.eastapp.backend.people.UserAccountRepository;
import com.eastapp.backend.places.GooglePlaceDetails;
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
            GooglePlaceDetails googlePlace,
            LoginIdentity creatorIdentity,
            String creatorFullName,
            String creatorPhoneE164,
            String profilePhotoKey,
            LocalDate birthDate,
            LocalDate startDate,
            LocalDate endDate,
            SystemRole creatorRole
    ) {
        Tenant tenant = new Tenant(companyCode, businessName, employeeIdPrefix);
        tenant.configureGoogleLocation(
                googlePlace.placeId(),
                googlePlace.displayName(),
                googlePlace.formattedAddress(),
                googlePlace.latitude(),
                googlePlace.longitude(),
                googlePlace.googleMapsUri()
        );
        tenant = tenantRepository.save(tenant);

        List<Role> roles = List.of(
                new Role(tenant, SystemRole.ADMIN, "Admin"),
                new Role(tenant, SystemRole.OWNER, "Owner"),
                new Role(tenant, SystemRole.HEAD, "Head"),
                new Role(tenant, SystemRole.MANAGER, "Manager"),
                new Role(tenant, SystemRole.SUPERVISOR, "Supervisor"),
                new Role(tenant, SystemRole.SENIOR_STAFF, "Senior Staff"),
                new Role(tenant, SystemRole.STAFF, "Staff"),
                new Role(tenant, SystemRole.PART_TIME, "Part Time")
        );
        roleRepository.saveAll(roles);
        Role role = roles.stream()
                .filter(candidate -> candidate.getSystemKey() == creatorRole)
                .findFirst()
                .orElseThrow();

        UserAccount creator = createContext(
                tenant,
                role,
                creatorIdentity,
                creatorFullName,
                creatorPhoneE164,
                profilePhotoKey,
                birthDate,
                startDate,
                endDate
        );
        userAccountRepository.saveAndFlush(creator);

        return new ProvisionedTenant(tenant, creator);
    }

    @Transactional
    public UserAccount addAdminContext(Tenant tenant, UserAccount sourceAdmin) {
        Tenant lockedTenant = tenantRepository.findLockedById(tenant.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "Tenant is unavailable while creating Admin context " + tenant.getId()
                ));
        Role adminRole = roleRepository
                .findByTenant_IdAndSystemKey(lockedTenant.getId(), SystemRole.ADMIN)
                .filter(Role::isActive)
                .orElseThrow(() -> new IllegalStateException(
                        "Active Admin role is unavailable for tenant " + lockedTenant.getId()
                ));
        return addContext(lockedTenant, adminRole, sourceAdmin);
    }

    private UserAccount addContext(
            Tenant tenant,
            Role role,
            UserAccount sourceUser
    ) {
        if (userAccountRepository.existsByIdentity_IdAndTenant_Id(
                sourceUser.getIdentity().getId(),
                tenant.getId()
        )) {
            UserAccount existing = userAccountRepository.findByTenant_IdAndIdentity_Id(
                    tenant.getId(),
                    sourceUser.getIdentity().getId()
            ).orElseThrow();
            existing.assignRole(role);
            existing.activate();
            existing.updateProfile(
                    sourceUser.getFullName(),
                    sourceUser.getPhoneE164(),
                    sourceUser.getProfilePhotoKey(),
                    sourceUser.getBirthDate(),
                    sourceUser.getStartDate(),
                    sourceUser.getEndDate()
            );
            return existing;
        }

        UserAccount user = createContext(
                tenant,
                role,
                sourceUser.getIdentity(),
                sourceUser.getFullName(),
                sourceUser.getPhoneE164(),
                sourceUser.getProfilePhotoKey(),
                sourceUser.getBirthDate(),
                sourceUser.getStartDate(),
                sourceUser.getEndDate()
        );
        return userAccountRepository.save(user);
    }

    private static UserAccount createContext(
            Tenant tenant,
            Role role,
            LoginIdentity identity,
            String fullName,
            String phoneE164,
            String profilePhotoKey,
            LocalDate birthDate,
            LocalDate startDate,
            LocalDate endDate
    ) {
        UserAccount user = new UserAccount(
                tenant,
                identity,
                tenant.allocateEmployeeId(),
                role
        );
        user.updateProfile(
                fullName,
                phoneE164,
                profilePhotoKey,
                birthDate,
                startDate,
                endDate
        );
        return user;
    }

    public record ProvisionedTenant(Tenant tenant, UserAccount creator) {
    }
}
