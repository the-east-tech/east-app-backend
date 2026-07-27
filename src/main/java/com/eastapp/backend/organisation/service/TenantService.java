package com.eastapp.backend.organisation.service;

import com.eastapp.backend.auth.security.AuthenticatedUser;
import com.eastapp.backend.common.error.ApiException;
import com.eastapp.backend.organisation.Tenant;
import com.eastapp.backend.organisation.TenantRepository;
import com.eastapp.backend.organisation.api.CreateTenantRequest;
import com.eastapp.backend.organisation.api.TenantResponse;
import com.eastapp.backend.organisation.api.UpdateTenantRequest;
import com.eastapp.backend.people.SystemRole;
import com.eastapp.backend.people.UserAccount;
import com.eastapp.backend.people.UserAccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class TenantService {

    private final TenantRepository tenantRepository;
    private final UserAccountRepository userAccountRepository;
    private final TenantProvisioningService tenantProvisioningService;

    public TenantService(
            TenantRepository tenantRepository,
            UserAccountRepository userAccountRepository,
            TenantProvisioningService tenantProvisioningService
    ) {
        this.tenantRepository = tenantRepository;
        this.userAccountRepository = userAccountRepository;
        this.tenantProvisioningService = tenantProvisioningService;
    }

    @Transactional(readOnly = true)
    public List<TenantResponse> list(AuthenticatedUser actor) {
        assertOwnerOrHead(actor);
        UserAccount current = currentActor(actor);
        if (!actor.isOwner()) {
            return List.of(TenantResponse.from(current.getTenant()));
        }
        return userAccountRepository.findAllContexts(current.getIdentity().getId()).stream()
                .filter(UserAccount::isActive)
                .filter(user -> user.getRole().isActive())
                .filter(user -> user.getRole().getSystemKey() == SystemRole.OWNER)
                .map(UserAccount::getTenant)
                .distinct()
                .sorted(Comparator.comparing(Tenant::getBusinessName, String.CASE_INSENSITIVE_ORDER))
                .map(TenantResponse::from)
                .toList();
    }

    @Transactional
    public TenantResponse create(AuthenticatedUser actor, CreateTenantRequest request) {
        assertOwner(actor);
        String companyCode = Tenant.normaliseCode(request.companyCode());
        String prefix = Tenant.normaliseEmployeeIdPrefix(request.employeeIdPrefix());
        assertUnique(companyCode, prefix);

        UserAccount creator = currentActor(actor);
        Map<UUID, UserAccount> existingOwnersByIdentity = new LinkedHashMap<>();
        userAccountRepository
                .findAllByRole_SystemKeyAndActiveTrueOrderByCreatedAtAsc(SystemRole.OWNER)
                .stream()
                .filter(user -> user.getIdentity().isActive())
                .filter(user -> user.getTenant().isActive())
                .filter(user -> user.getRole().isActive())
                .forEach(user -> existingOwnersByIdentity.putIfAbsent(
                        user.getIdentity().getId(), user
                ));

        TenantProvisioningService.ProvisionedTenant provisioned = tenantProvisioningService.provision(
                companyCode, request.businessName(), prefix,
                creator.getIdentity(), creator.getFullName(), creator.getPhoneE164(),
                creator.getProfilePhotoKey(), creator.getBirthDate(),
                creator.getStartDate(), creator.getEndDate()
        );

        existingOwnersByIdentity.values().stream()
                .filter(owner -> !owner.getIdentity().getId().equals(creator.getIdentity().getId()))
                .forEach(owner -> tenantProvisioningService.addOwnerContext(provisioned, owner));

        return TenantResponse.from(provisioned.tenant());
    }

    @Transactional
    public TenantResponse update(
            AuthenticatedUser actor,
            UUID tenantId,
            UpdateTenantRequest request
    ) {
        assertOwnerOrHead(actor);
        UserAccount current = currentActor(actor);
        if (tenantId.equals(actor.tenantId()) && !request.active()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "CURRENT_TENANT_DEACTIVATION",
                    "The current business cannot be deactivated while it is active in this session."
            );
        }

        Tenant tenant;
        if (actor.isOwner()) {
            UserAccount targetMembership = userAccountRepository
                    .findByTenant_IdAndIdentity_Id(tenantId, current.getIdentity().getId())
                    .filter(UserAccount::isActive)
                    .filter(user -> user.getRole().isActive())
                    .filter(user -> user.getRole().getSystemKey() == SystemRole.OWNER)
                    .orElseThrow(() -> new ApiException(
                            HttpStatus.FORBIDDEN,
                            "TENANT_ACCESS_DENIED",
                            "This tenant is not assigned to the current Owner login."
                    ));
            tenant = targetMembership.getTenant();
        } else {
            if (!tenantId.equals(actor.tenantId())) {
                throw new ApiException(
                        HttpStatus.FORBIDDEN,
                        "TENANT_ACCESS_DENIED",
                        "Head users may manage only their current tenant."
                );
            }
            tenant = current.getTenant();
        }

        tenant.update(request.businessName(), request.active());
        return TenantResponse.from(tenant);
    }

    private static void assertOwner(AuthenticatedUser actor) {
        if (!actor.isOwner()) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "OWNER_REQUIRED",
                    "Only Owner users may create tenants."
            );
        }
    }

    private static void assertOwnerOrHead(AuthenticatedUser actor) {
        if (!actor.isOwner() && actor.systemRole() != SystemRole.HEAD) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "TENANT_MANAGEMENT_DENIED",
                    "Only Owner and Head users may manage tenants."
            );
        }
    }

    private UserAccount currentActor(AuthenticatedUser actor) {
        return userAccountRepository.findByIdAndTenant_Id(actor.userId(), actor.tenantId())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNAUTHORIZED,
                        "INVALID_SESSION",
                        "The current user is unavailable."
                ));
    }

    private void assertUnique(String companyCode, String prefix) {
        if (tenantRepository.existsByCompanyCode(companyCode)) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "COMPANY_CODE_EXISTS", "This company code already exists."
            );
        }
        if (tenantRepository.existsByEmployeeIdPrefix(prefix)) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "EMPLOYEE_PREFIX_EXISTS", "This employee ID prefix already exists."
            );
        }
    }
}
