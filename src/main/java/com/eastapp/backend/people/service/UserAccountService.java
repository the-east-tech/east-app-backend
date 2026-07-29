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
import com.eastapp.backend.organisation.service.TenantProvisioningService;
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
    private final TenantProvisioningService tenantProvisioningService;

    public UserAccountService(
            UserAccountRepository userAccountRepository,
            LoginIdentityRepository loginIdentityRepository,
            UserSessionRepository userSessionRepository,
            TenantRepository tenantRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            EmployeeIdService employeeIdService,
            TenantProvisioningService tenantProvisioningService
    ) {
        this.userAccountRepository = userAccountRepository;
        this.loginIdentityRepository = loginIdentityRepository;
        this.userSessionRepository = userSessionRepository;
        this.tenantRepository = tenantRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.employeeIdService = employeeIdService;
        this.tenantProvisioningService = tenantProvisioningService;
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
                        Sort.Order.asc("identity.fullName").ignoreCase(),
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

    /**
     * Creates a membership only in the actor's active tenant. The tenant is never
     * accepted from the client. When the phone already belongs to a global identity,
     * that identity is reused and receives a new tenant-specific employee ID.
     */
    @Transactional
    public UserResponse create(AuthenticatedUser actor, CreateUserRequest request) {
        assertUserCreationAllowed(actor);

        UUID tenantId = actor.tenantId();
        Tenant tenant = tenantRepository.findById(tenantId)
                .filter(Tenant::isActive)
                .orElseThrow(() -> notFound("TENANT_NOT_FOUND", "Active tenant not found."));
        Role role = findActiveRole(tenantId, request.roleId());
        assertRoleMayBeAssigned(actor, role);

        String phoneE164 = LoginIdentity.normalisePhone(request.phoneE164());
        LoginIdentity identity = loginIdentityRepository.findByPhoneE164(phoneE164)
                .map(existing -> reuseIdentity(existing, request, tenantId))
                .orElseGet(() -> createIdentity(request, phoneE164));

        UserAccount membership = createUserAccount(
                tenant,
                identity,
                employeeIdService.allocate(tenantId),
                role,
                request
        );
        membership = userAccountRepository.save(membership);

        if (role.getSystemKey() == SystemRole.OWNER) {
            UserAccount sourceOwner = membership;
            tenantRepository.findAllByActiveTrueOrderByBusinessNameAsc().stream()
                    .filter(other -> !other.getId().equals(tenantId))
                    .forEach(other -> tenantProvisioningService.addOwnerContext(other, sourceOwner));
        }

        return UserResponse.from(membership);
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
        assertPhoneAvailableForIdentity(request.phoneE164(), target.getIdentity().getId());

        if (target.getRole().getSystemKey() == SystemRole.OWNER) {
            assertOwnerAccountRemainsOwner(newRole, request.active());
            target.updateProfile(
                    request.fullName(), request.phoneE164(), request.profilePhotoKey(),
                    request.birthDate(), request.startDate(), request.endDate()
            );
            return UserResponse.from(target);
        }

        if (newRole.getSystemKey() == SystemRole.OWNER && !request.active()) {
            throw conflict(
                    "OWNER_ACCOUNT_ACTIVE_REQUIRED",
                    "A user promoted to Owner must remain active."
            );
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

        if (newRole.getSystemKey() == SystemRole.OWNER) {
            tenantRepository.findAllByActiveTrueOrderByBusinessNameAsc().stream()
                    .filter(tenant -> !tenant.getId().equals(target.getTenant().getId()))
                    .forEach(tenant -> tenantProvisioningService.addOwnerContext(tenant, target));
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

    private void assertPhoneAvailableForIdentity(String phoneE164, UUID identityId) {
        String normalised = LoginIdentity.normalisePhone(phoneE164);
        loginIdentityRepository.findByPhoneE164(normalised)
                .filter(existing -> !existing.getId().equals(identityId))
                .ifPresent(existing -> {
                    throw conflict(
                            "PHONE_ALREADY_USED",
                            "This phone number belongs to another EastApp login."
                    );
                });
    }

    private LoginIdentity reuseIdentity(
            LoginIdentity identity,
            CreateUserRequest request,
            UUID tenantId
    ) {
        if (!identity.isActive()) {
            throw conflict("IDENTITY_INACTIVE", "This person's global login is inactive.");
        }
        if (userAccountRepository.existsByIdentity_IdAndTenant_Id(identity.getId(), tenantId)) {
            throw conflict(
                    "USER_ALREADY_IN_BUSINESS",
                    "This person already has an employee ID in the current business."
            );
        }
        identity.updateProfile(
                request.fullName(), request.phoneE164(),
                request.profilePhotoKey(), request.birthDate()
        );
        return identity;
    }

    private LoginIdentity createIdentity(CreateUserRequest request, String phoneE164) {
        String password = request.password() == null ? "" : request.password().trim();
        if (password.length() < 4) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "PASSWORD_REQUIRED",
                    "Password is required when creating a new person. Leave it blank only when adding an existing person to this business."
            );
        }
        return loginIdentityRepository.save(new LoginIdentity(
                passwordEncoder.encode(password),
                request.fullName(),
                phoneE164,
                request.profilePhotoKey(),
                request.birthDate()
        ));
    }

    private static void assertUserCreationAllowed(AuthenticatedUser actor) {
        if (!actor.isOwner() && actor.systemRole() != SystemRole.HEAD) {
            throw forbidden(
                    "USER_CREATION_DENIED",
                    "Only Owner and Head users may create users."
            );
        }
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

    private static UserAccount createUserAccount(
            Tenant tenant,
            LoginIdentity identity,
            String employeeId,
            Role role,
            CreateUserRequest request
    ) {
        UserAccount user = new UserAccount(tenant, identity, employeeId, role);
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
