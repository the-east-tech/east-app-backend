package com.eastapp.backend.people.service;

import com.eastapp.backend.auth.LoginIdentity;
import com.eastapp.backend.auth.LoginIdentityRepository;
import com.eastapp.backend.auth.UserSession;
import com.eastapp.backend.auth.UserSessionRepository;
import com.eastapp.backend.auth.security.AuthenticatedUser;
import com.eastapp.backend.common.api.PageResponse;
import com.eastapp.backend.common.error.ApiException;
import com.eastapp.backend.organisation.Tenant;
import com.eastapp.backend.organisation.TenantRepository;
import com.eastapp.backend.organisation.service.EmployeeIdService;
import com.eastapp.backend.people.Role;
import com.eastapp.backend.people.RoleRepository;
import com.eastapp.backend.people.SystemRole;
import com.eastapp.backend.people.UserAccount;
import com.eastapp.backend.people.UserAccountRepository;
import com.eastapp.backend.people.api.CreateUserRequest;
import com.eastapp.backend.people.api.ResetPasswordRequest;
import com.eastapp.backend.people.api.UpdateUserRequest;
import com.eastapp.backend.people.api.UserResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class UserAccountService {

    private final UserAccountRepository userAccountRepository;
    private final LoginIdentityRepository loginIdentityRepository;
    private final UserSessionRepository userSessionRepository;
    private final TenantRepository tenantRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmployeeIdService employeeIdService;

    public UserAccountService(
            UserAccountRepository userAccountRepository,
            LoginIdentityRepository loginIdentityRepository,
            UserSessionRepository userSessionRepository,
            TenantRepository tenantRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            EmployeeIdService employeeIdService
    ) {
        this.userAccountRepository = userAccountRepository;
        this.loginIdentityRepository = loginIdentityRepository;
        this.userSessionRepository = userSessionRepository;
        this.tenantRepository = tenantRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.employeeIdService = employeeIdService;
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> list(
            UUID tenantId,
            String search,
            Boolean active,
            int page,
            int size
    ) {
        String resolvedSearch = search == null ? "" : search.trim();
        PageRequest pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), 100),
                Sort.by(
                        Sort.Order.asc("fullName").ignoreCase(),
                        Sort.Order.asc("id")
                )
        );
        return PageResponse.from(
                userAccountRepository.searchByTenant(tenantId, resolvedSearch, active, pageable),
                UserResponse::from
        );
    }

    @Transactional(readOnly = true)
    public UserResponse get(UUID tenantId, UUID userId) {
        return UserResponse.from(findUser(tenantId, userId));
    }

    @Transactional
    public UserResponse create(AuthenticatedUser actor, CreateUserRequest request) {
        UUID targetTenantId = request.tenantId();
        assertTenantMayBeManaged(actor, targetTenantId);

        Tenant targetTenant = tenantRepository.findById(targetTenantId)
                .filter(Tenant::isActive)
                .orElseThrow(() -> notFound("TENANT_NOT_FOUND", "Active tenant not found."));
        Role role = findActiveRole(targetTenantId, request.roleId());
        assertRoleMayBeAssigned(actor, role);

        LoginIdentity identity = loginIdentityRepository.save(
                new LoginIdentity(passwordEncoder.encode(request.password()))
        );

        if (role.getSystemKey() == SystemRole.OWNER) {
            return createOwnerAcrossActiveTenants(identity, request, targetTenantId);
        }

        UserAccount user = createUserAccount(
                targetTenant,
                identity,
                employeeIdService.allocate(targetTenantId),
                role,
                request
        );
        return UserResponse.from(userAccountRepository.save(user));
    }

    @Transactional
    public UserResponse update(
            AuthenticatedUser actor,
            UUID userId,
            UpdateUserRequest request
    ) {
        UserAccount target = findUser(actor.tenantId(), userId);
        assertActorMayManageUser(actor, target);

        Role newRole = findActiveRole(actor.tenantId(), request.roleId());
        assertRoleMayBeAssigned(actor, newRole);

        if (target.getRole().getSystemKey() == SystemRole.OWNER) {
            assertOwnerAccountRemainsOwner(newRole, request.active());
            userAccountRepository.findAllContexts(target.getIdentity().getId()).forEach(context ->
                    context.updateProfile(
                            request.fullName(), request.phoneE164(), request.profilePhotoKey(),
                            request.birthDate(), request.startDate(), request.endDate()
                    )
            );
            return UserResponse.from(target);
        }

        target.updateProfile(
                request.fullName(), request.phoneE164(), request.profilePhotoKey(),
                request.birthDate(), request.startDate(), request.endDate()
        );
        target.assignRole(newRole);

        if (request.active()) {
            target.activate();
        } else {
            target.deactivate();
            revokeSessions(target.getId());
        }

        return UserResponse.from(target);
    }

    @Transactional
    public void resetPassword(
            AuthenticatedUser actor,
            UUID userId,
            ResetPasswordRequest request
    ) {
        UserAccount target = findUser(actor.tenantId(), userId);
        assertActorMayManageUser(actor, target);
        target.getIdentity().changePasswordHash(passwordEncoder.encode(request.password()));
        revokeIdentitySessions(target.getIdentity().getId());
    }

    private void assertTenantMayBeManaged(AuthenticatedUser actor, UUID targetTenantId) {
        if (targetTenantId.equals(actor.tenantId())) {
            return;
        }
        if (!actor.isOwner()) {
            throw forbidden(
                    "TENANT_USER_CREATION_DENIED",
                    "Only Owner users may create a user for another tenant."
            );
        }

        UserAccount current = findUser(actor.tenantId(), actor.userId());
        userAccountRepository
                .findByTenant_IdAndIdentity_Id(targetTenantId, current.getIdentity().getId())
                .filter(UserAccount::isActive)
                .filter(user -> user.getRole().isActive())
                .filter(user -> user.getRole().getSystemKey() == SystemRole.OWNER)
                .orElseThrow(() -> forbidden(
                        "TENANT_USER_CREATION_DENIED",
                        "This tenant is not assigned to the current Owner login."
                ));
    }

    private UserAccount findUser(UUID tenantId, UUID userId) {
        return userAccountRepository.findByIdAndTenant_Id(userId, tenantId)
                .orElseThrow(() -> notFound("USER_NOT_FOUND", "User not found."));
    }

    private Role findActiveRole(UUID tenantId, UUID roleId) {
        Role role = roleRepository.findByIdAndTenant_Id(roleId, tenantId)
                .orElseThrow(() -> notFound("ROLE_NOT_FOUND", "Role not found."));
        if (!role.isActive()) {
            throw conflict("ROLE_INACTIVE", "Inactive roles cannot be assigned.");
        }
        return role;
    }

    private static void assertActorMayManageUser(
            AuthenticatedUser actor,
            UserAccount target
    ) {
        if (actor.isOwner()) {
            return;
        }
        if (actor.systemRole() == SystemRole.HEAD) {
            if (target.getRole().getSystemKey() == SystemRole.OWNER) {
                throw forbidden("PROTECTED_USER", "Head users cannot edit Owner users.");
            }
            return;
        }
        if (!actor.isManager()) {
            throw forbidden("USER_MANAGEMENT_DENIED", "User management is not permitted.");
        }

        SystemRole targetRole = target.getRole().getSystemKey();
        if (targetRole == SystemRole.OWNER
                || targetRole == SystemRole.HEAD
                || targetRole == SystemRole.MANAGER) {
            throw forbidden(
                    "PROTECTED_USER",
                    "Managers cannot edit Owner, Head or Manager users."
            );
        }
    }

    private static void assertRoleMayBeAssigned(AuthenticatedUser actor, Role role) {
        if (actor.isOwner()) {
            return;
        }
        if (actor.systemRole() == SystemRole.HEAD) {
            if (role.getSystemKey() == SystemRole.OWNER) {
                throw forbidden(
                        "ROLE_ASSIGNMENT_DENIED",
                        "Only Owner users may assign the Owner role."
                );
            }
            return;
        }
        if (!actor.isManager()) {
            throw forbidden("ROLE_ASSIGNMENT_DENIED", "Role assignment is not permitted.");
        }

        if (role.getSystemKey() != SystemRole.STAFF_1
                && role.getSystemKey() != SystemRole.STAFF_2) {
            throw forbidden(
                    "ROLE_ASSIGNMENT_DENIED",
                    "Managers may assign only Staff1 or Staff2 roles."
            );
        }
    }

    private UserResponse createOwnerAcrossActiveTenants(
            LoginIdentity identity,
            CreateUserRequest request,
            UUID selectedTenantId
    ) {
        UserAccount selectedOwner = null;

        for (Tenant tenant : tenantRepository.findAllByActiveTrueOrderByBusinessNameAsc()) {
            Role ownerRole = roleRepository
                    .findByTenant_IdAndSystemKey(tenant.getId(), SystemRole.OWNER)
                    .filter(Role::isActive)
                    .orElseThrow(() -> conflict(
                            "OWNER_ROLE_UNAVAILABLE",
                            "The Owner role is unavailable for " + tenant.getBusinessName() + "."
                    ));

            UserAccount owner = createUserAccount(
                    tenant,
                    identity,
                    employeeIdService.allocate(tenant.getId()),
                    ownerRole,
                    request
            );
            userAccountRepository.save(owner);
            if (tenant.getId().equals(selectedTenantId)) {
                selectedOwner = owner;
            }
        }

        if (selectedOwner == null) {
            throw notFound("TENANT_NOT_FOUND", "Selected active tenant not found.");
        }
        return UserResponse.from(selectedOwner);
    }

    private static UserAccount createUserAccount(
            Tenant tenant,
            LoginIdentity identity,
            String employeeId,
            Role role,
            CreateUserRequest request
    ) {
        UserAccount user = new UserAccount(
                tenant, identity, employeeId, request.fullName(), request.phoneE164(), role
        );
        user.updateProfile(
                request.fullName(), request.phoneE164(), request.profilePhotoKey(),
                request.birthDate(), request.startDate(), request.endDate()
        );
        return user;
    }

    private static void assertOwnerAccountRemainsOwner(Role newRole, boolean active) {
        if (newRole.getSystemKey() != SystemRole.OWNER || !active) {
            throw conflict(
                    "OWNER_ACCOUNT_PROTECTED",
                    "Owner access is system-wide and cannot be demoted or deactivated here."
            );
        }
    }

    private void revokeIdentitySessions(UUID identityId) {
        Instant now = Instant.now();
        List<UserSession> sessions = userSessionRepository
                .findAllByIdentity_IdAndRevokedAtIsNull(identityId);
        sessions.forEach(session -> session.revoke(now));
    }

    private void revokeSessions(UUID userId) {
        Instant now = Instant.now();
        List<UserSession> sessions = userSessionRepository
                .findAllByUserAccount_IdAndRevokedAtIsNull(userId);
        sessions.forEach(session -> session.revoke(now));
    }

    private static ApiException notFound(String code, String message) {
        return new ApiException(HttpStatus.NOT_FOUND, code, message);
    }

    private static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

    private static ApiException forbidden(String code, String message) {
        return new ApiException(HttpStatus.FORBIDDEN, code, message);
    }
}
