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
import com.eastapp.backend.places.GooglePlaceDetails;
import com.eastapp.backend.places.service.GooglePlacesService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

@Service
public class TenantService {

    private final TenantRepository tenantRepository;
    private final UserAccountRepository userAccountRepository;
    private final TenantProvisioningService tenantProvisioningService;
    private final SystemAdminProvisioningService systemAdminProvisioningService;
    private final GooglePlacesService googlePlacesService;
    private final TransactionTemplate transactionTemplate;

    public TenantService(
            TenantRepository tenantRepository,
            UserAccountRepository userAccountRepository,
            TenantProvisioningService tenantProvisioningService,
            SystemAdminProvisioningService systemAdminProvisioningService,
            GooglePlacesService googlePlacesService,
            PlatformTransactionManager transactionManager
    ) {
        this.tenantRepository = tenantRepository;
        this.userAccountRepository = userAccountRepository;
        this.tenantProvisioningService = tenantProvisioningService;
        this.systemAdminProvisioningService = systemAdminProvisioningService;
        this.googlePlacesService = googlePlacesService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * Normal business details are always scoped to the active context. Owners use
     * /api/v1/auth/contexts only for the minimal business switcher list.
     */
    @Transactional(readOnly = true)
    public List<TenantResponse> list(AuthenticatedUser actor) {
        assertOwner(actor);
        return List.of(TenantResponse.from(currentActor(actor).getTenant()));
    }

    /**
     * Business creation is the deliberate Owner/Admin global exception. Google is
     * resolved before the short database transaction begins.
     */
    public TenantResponse create(AuthenticatedUser actor, CreateTenantRequest request) {
        assertOwner(actor);
        GooglePlaceDetails googlePlace = googlePlacesService.placeDetails(request.googlePlaceId());
        TenantResponse response = transactionTemplate.execute(
                status -> createInTransaction(actor, request, googlePlace)
        );
        if (response == null) {
            throw new IllegalStateException("Tenant creation transaction returned no response");
        }
        return response;
    }

    private TenantResponse createInTransaction(
            AuthenticatedUser actor,
            CreateTenantRequest request,
            GooglePlaceDetails googlePlace
    ) {
        String companyCode = Tenant.normaliseCode(request.companyCode());
        String prefix = Tenant.normaliseEmployeeIdPrefix(request.employeeIdPrefix());
        assertUnique(companyCode, prefix);

        UserAccount creator = currentActor(actor);
        TenantProvisioningService.ProvisionedTenant provisioned = tenantProvisioningService.provision(
                companyCode, request.businessName(), prefix,
                googlePlace,
                creator.getIdentity(), creator.getFullName(), creator.getPhoneE164(),
                creator.getProfilePhotoKey(), creator.getBirthDate(),
                creator.getStartDate(), creator.getEndDate()
        );

        if (creator.getRole().getSystemKey() == SystemRole.ADMIN) {
            systemAdminProvisioningService.promoteAdminContext(provisioned.owner());
        } else {
            // Only the Owner who created this business receives its Owner context.
            // The one platform Admin is maintained separately and remains hidden.
            systemAdminProvisioningService.ensureAdminContext(provisioned.tenant());
        }

        return TenantResponse.from(provisioned.tenant());
    }

    public TenantResponse update(
            AuthenticatedUser actor,
            UUID tenantId,
            UpdateTenantRequest request
    ) {
        assertOwner(actor);
        if (!tenantId.equals(actor.tenantId())) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "TENANT_ACCESS_DENIED",
                    "Switch business context before viewing or editing another business."
            );
        }
        GooglePlaceDetails googlePlace = googlePlacesService.placeDetails(request.googlePlaceId());
        TenantResponse response = transactionTemplate.execute(
                status -> updateInTransaction(actor, request, googlePlace)
        );
        if (response == null) {
            throw new IllegalStateException("Tenant update transaction returned no response");
        }
        return response;
    }

    private TenantResponse updateInTransaction(
            AuthenticatedUser actor,
            UpdateTenantRequest request,
            GooglePlaceDetails googlePlace
    ) {
        UserAccount current = currentActor(actor);
        if (!request.active()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "CURRENT_TENANT_DEACTIVATION",
                    "The active business cannot be deactivated. Switch away before changing its lifecycle."
            );
        }

        Tenant tenant = current.getTenant();
        tenant.update(request.businessName(), true);
        tenant.configureGoogleLocation(
                googlePlace.placeId(),
                googlePlace.displayName(),
                googlePlace.formattedAddress(),
                googlePlace.latitude(),
                googlePlace.longitude(),
                googlePlace.googleMapsUri()
        );
        return TenantResponse.from(tenant);
    }

    private static void assertOwner(AuthenticatedUser actor) {
        if (!actor.isOwner()) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "OWNER_REQUIRED",
                    "Only Owner users may create businesses."
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
