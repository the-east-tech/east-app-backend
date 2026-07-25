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
import java.util.List;
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
        UserAccount current = currentActor(actor);
        return userAccountRepository.findAllContexts(current.getIdentity().getId()).stream()
                .filter(user -> user.getRole().getSystemKey() == SystemRole.HEAD)
                .map(UserAccount::getTenant)
                .distinct()
                .sorted(Comparator.comparing(Tenant::getBusinessName, String.CASE_INSENSITIVE_ORDER))
                .map(TenantResponse::from)
                .toList();
    }

    @Transactional
    public TenantResponse create(AuthenticatedUser actor, CreateTenantRequest request) {
        String companyCode = Tenant.normaliseCode(request.companyCode());
        String prefix = Tenant.normaliseEmployeeIdPrefix(request.employeeIdPrefix());
        assertUnique(companyCode, prefix);

        UserAccount creator = currentActor(actor);
        TenantProvisioningService.ProvisionedTenant provisioned = tenantProvisioningService.provision(
                companyCode,
                request.businessName(),
                prefix,
                creator.getIdentity(),
                creator.getFullName(),
                creator.getPhoneE164(),
                creator.getProfilePhotoKey(),
                creator.getBirthDate(),
                creator.getStartDate(),
                creator.getEndDate()
        );
        return TenantResponse.from(provisioned.tenant());
    }

    @Transactional
    public TenantResponse update(
            AuthenticatedUser actor,
            UUID tenantId,
            UpdateTenantRequest request
    ) {
        UserAccount current = currentActor(actor);
        if (tenantId.equals(actor.tenantId()) && !request.active()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "CURRENT_TENANT_DEACTIVATION",
                    "Switch to another business before deactivating the current tenant."
            );
        }
        UserAccount targetMembership = userAccountRepository
                .findByTenant_IdAndIdentity_Id(tenantId, current.getIdentity().getId())
                .filter(user -> user.getRole().getSystemKey() == SystemRole.HEAD)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.FORBIDDEN,
                        "TENANT_ACCESS_DENIED",
                        "This tenant is not assigned to the current Head login."
                ));

        Tenant tenant = targetMembership.getTenant();
        tenant.update(request.businessName(), request.active());
        return TenantResponse.from(tenant);
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
                    HttpStatus.CONFLICT,
                    "COMPANY_CODE_EXISTS",
                    "This company code already exists."
            );
        }
        if (tenantRepository.existsByEmployeeIdPrefix(prefix)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "EMPLOYEE_PREFIX_EXISTS",
                    "This employee ID prefix already exists."
            );
        }
    }
}
