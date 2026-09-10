package com.eastapp.backend.organisation.service;

import com.eastapp.backend.auth.LoginIdentity;
import com.eastapp.backend.auth.LoginIdentityRepository;
import com.eastapp.backend.organisation.Tenant;
import com.eastapp.backend.organisation.TenantRepository;
import com.eastapp.backend.people.Role;
import com.eastapp.backend.people.RoleRepository;
import com.eastapp.backend.people.SystemRole;
import com.eastapp.backend.people.UserAccount;
import com.eastapp.backend.people.UserAccountRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Maintains exactly one platform administrator identity: the identity created
 * by EastApp's initial setup. The account is tenant-scoped like every other
 * user, so each business still receives its own employee ID.
 */
@Service
public class SystemAdminProvisioningService {

    private final LoginIdentityRepository loginIdentityRepository;
    private final TenantRepository tenantRepository;
    private final RoleRepository roleRepository;
    private final UserAccountRepository userAccountRepository;
    private final TenantProvisioningService tenantProvisioningService;

    public SystemAdminProvisioningService(
            LoginIdentityRepository loginIdentityRepository,
            TenantRepository tenantRepository,
            RoleRepository roleRepository,
            UserAccountRepository userAccountRepository,
            TenantProvisioningService tenantProvisioningService
    ) {
        this.loginIdentityRepository = loginIdentityRepository;
        this.tenantRepository = tenantRepository;
        this.roleRepository = roleRepository;
        this.userAccountRepository = userAccountRepository;
        this.tenantProvisioningService = tenantProvisioningService;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void reconcileExistingAdmin() {
        LoginIdentity adminIdentity = loginIdentityRepository
                .findFirstByOrderByCreatedAtAscIdAsc()
                .orElse(null);
        if (adminIdentity == null) return;

        UserAccount sourceAdmin = sourceAdmin(adminIdentity);
        if (sourceAdmin == null) return;

        for (Tenant tenant : tenantRepository.findAllByOrderByBusinessNameAsc()) {
            ensureAdminRole(tenant);
            tenantProvisioningService.addAdminContext(tenant, sourceAdmin);
        }
    }

    @Transactional
    public UserAccount promoteAdminContext(UserAccount user) {
        Role adminRole = ensureAdminRole(user.getTenant());
        user.assignRole(adminRole);
        user.activate();
        return userAccountRepository.saveAndFlush(user);
    }

    @Transactional
    public void ensureAdminContext(Tenant tenant) {
        LoginIdentity adminIdentity = loginIdentityRepository
                .findFirstByOrderByCreatedAtAscIdAsc()
                .orElse(null);
        if (adminIdentity == null) return;

        UserAccount sourceAdmin = sourceAdmin(adminIdentity);
        if (sourceAdmin == null) return;

        ensureAdminRole(tenant);
        tenantProvisioningService.addAdminContext(tenant, sourceAdmin);
    }

    private Role ensureAdminRole(Tenant tenant) {
        return roleRepository.findByTenant_IdAndSystemKey(tenant.getId(), SystemRole.ADMIN)
                .orElseGet(() -> roleRepository.save(new Role(tenant, SystemRole.ADMIN, "Admin")));
    }

    private UserAccount sourceAdmin(LoginIdentity adminIdentity) {
        Tenant initialTenant = tenantRepository.findFirstByOrderByCreatedAtAscIdAsc().orElse(null);
        if (initialTenant != null) {
            UserAccount initialContext = userAccountRepository
                    .findByTenant_IdAndIdentity_Id(initialTenant.getId(), adminIdentity.getId())
                    .orElse(null);
            if (initialContext != null) return initialContext;
        }

        List<UserAccount> contexts = userAccountRepository.findAllContexts(adminIdentity.getId());
        return contexts.isEmpty() ? null : contexts.getFirst();
    }
}
