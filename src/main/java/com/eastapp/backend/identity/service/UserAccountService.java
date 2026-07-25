package com.eastapp.backend.identity.service;

import com.eastapp.backend.common.api.PageResponse;
import com.eastapp.backend.identity.LoginIdentity;
import com.eastapp.backend.identity.LoginIdentityRepository;
import com.eastapp.backend.identity.Role;
import com.eastapp.backend.identity.RoleRepository;
import com.eastapp.backend.identity.SystemRole;
import com.eastapp.backend.identity.Tenant;
import com.eastapp.backend.identity.TenantRepository;
import com.eastapp.backend.identity.UserAccount;
import com.eastapp.backend.identity.UserAccountRepository;
import com.eastapp.backend.identity.UserSession;
import com.eastapp.backend.identity.UserSessionRepository;
import com.eastapp.backend.identity.api.CreateUserRequest;
import com.eastapp.backend.identity.api.ResetPasswordRequest;
import com.eastapp.backend.identity.api.UpdateUserRequest;
import com.eastapp.backend.identity.api.UserResponse;
import com.eastapp.backend.identity.auth.AuthenticatedUser;
import com.eastapp.backend.identity.support.ApiException;
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

    public UserAccountService(
            UserAccountRepository userAccountRepository,
            LoginIdentityRepository loginIdentityRepository,
            UserSessionRepository userSessionRepository,
            TenantRepository tenantRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userAccountRepository = userAccountRepository;
        this.loginIdentityRepository = loginIdentityRepository;
        this.userSessionRepository = userSessionRepository;
        this.tenantRepository = tenantRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
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
        String employeeId = UserAccount.normaliseEmployeeId(request.employeeId());
        if (userAccountRepository.existsByTenant_IdAndEmployeeId(actor.tenantId(), employeeId)) {
            throw conflict("EMPLOYEE_ID_EXISTS", "This Employee ID already exists.");
        }

        Tenant tenant = tenantRepository.findById(actor.tenantId())
                .orElseThrow(() -> notFound("TENANT_NOT_FOUND", "Tenant not found."));
        Role role = findActiveRole(actor.tenantId(), request.roleId());
        assertRoleMayBeAssigned(actor, role);

        LoginIdentity identity = loginIdentityRepository.save(
                new LoginIdentity(passwordEncoder.encode(request.password()))
        );
        UserAccount user = new UserAccount(
                tenant,
                identity,
                employeeId,
                request.fullName(),
                request.phoneE164(),
                role
        );
        user.updateProfile(
                request.fullName(),
                request.phoneE164(),
                request.profilePhotoKey(),
                request.birthDate(),
                request.startDate(),
                request.endDate()
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
        assertActiveHeadRemains(target, newRole, request.active());

        target.updateProfile(
                request.fullName(),
                request.phoneE164(),
                request.profilePhotoKey(),
                request.birthDate(),
                request.startDate(),
                request.endDate()
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
        if (actor.isHead()) {
            return;
        }
        if (!actor.isManager()) {
            throw forbidden("USER_MANAGEMENT_DENIED", "User management is not permitted.");
        }

        SystemRole targetRole = target.getRole().getSystemKey();
        if (targetRole == SystemRole.HEAD || targetRole == SystemRole.MANAGER) {
            throw forbidden(
                    "PROTECTED_USER",
                    "Managers cannot edit Head or Manager users."
            );
        }
    }

    private static void assertRoleMayBeAssigned(AuthenticatedUser actor, Role role) {
        if (actor.isHead()) {
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

    private void assertActiveHeadRemains(
            UserAccount target,
            Role newRole,
            boolean active
    ) {
        if (target.getRole().getSystemKey() != SystemRole.HEAD) {
            return;
        }
        if (newRole.getSystemKey() == SystemRole.HEAD && active) {
            return;
        }

        long activeHeads = userAccountRepository
                .countByTenant_IdAndRole_SystemKeyAndActiveTrue(
                        target.getTenant().getId(),
                        SystemRole.HEAD
                );
        if (activeHeads <= 1) {
            throw conflict(
                    "LAST_ACTIVE_HEAD",
                    "At least one active Head user must remain."
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
