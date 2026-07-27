package com.eastapp.backend.people.service;

import com.eastapp.backend.auth.security.AuthenticatedUser;
import com.eastapp.backend.common.error.ApiException;
import com.eastapp.backend.organisation.Tenant;
import com.eastapp.backend.organisation.TenantRepository;
import com.eastapp.backend.people.Role;
import com.eastapp.backend.people.RoleRepository;
import com.eastapp.backend.people.SystemRole;
import com.eastapp.backend.people.UserAccount;
import com.eastapp.backend.people.UserAccountRepository;
import com.eastapp.backend.people.api.CreateRoleRequest;
import com.eastapp.backend.people.api.RoleResponse;
import com.eastapp.backend.people.api.UpdateRoleRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class RoleService {

    private final RoleRepository roleRepository;
    private final TenantRepository tenantRepository;
    private final UserAccountRepository userAccountRepository;

    public RoleService(
            RoleRepository roleRepository,
            TenantRepository tenantRepository,
            UserAccountRepository userAccountRepository
    ) {
        this.roleRepository = roleRepository;
        this.tenantRepository = tenantRepository;
        this.userAccountRepository = userAccountRepository;
    }

    @Transactional(readOnly = true)
    public List<RoleResponse> list(AuthenticatedUser actor, UUID requestedTenantId) {
        UUID tenantId = requestedTenantId == null ? actor.tenantId() : requestedTenantId;
        assertRoleListAccess(actor, tenantId);
        return roleRepository.findAllByTenant_IdOrderByNameAsc(tenantId)
                .stream()
                .map(role -> RoleResponse.from(
                        role, userAccountRepository.countByRole_Id(role.getId())
                ))
                .toList();
    }

    @Transactional
    public RoleResponse create(UUID tenantId, CreateRoleRequest request) {
        if (roleRepository.existsByTenant_IdAndNameIgnoreCase(tenantId, request.name().trim())) {
            throw conflict("ROLE_NAME_EXISTS", "A role with this name already exists.");
        }

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> notFound("TENANT_NOT_FOUND", "Tenant not found."));
        Role role = roleRepository.save(Role.custom(tenant, request.name()));
        return RoleResponse.from(role, 0);
    }

    @Transactional
    public RoleResponse update(UUID tenantId, UUID roleId, UpdateRoleRequest request) {
        Role role = findRole(tenantId, roleId);

        if (roleRepository.existsByTenant_IdAndNameIgnoreCaseAndIdNot(
                tenantId, request.name().trim(), roleId
        )) {
            throw conflict("ROLE_NAME_EXISTS", "A role with this name already exists.");
        }

        if ((role.getSystemKey() == SystemRole.OWNER
                || role.getSystemKey() == SystemRole.HEAD)
                && !request.active()) {
            throw conflict(
                    "PROTECTED_ROLE_REQUIRED",
                    "The Owner and Head roles cannot be deactivated."
            );
        }

        role.rename(request.name());
        if (request.active()) {
            role.activate();
        } else {
            role.deactivate();
        }

        return RoleResponse.from(
                role, userAccountRepository.countByRole_Id(role.getId())
        );
    }

    @Transactional
    public void delete(UUID tenantId, UUID roleId) {
        Role role = findRole(tenantId, roleId);
        if (role.isBuiltIn()) {
            throw conflict("BUILT_IN_ROLE", "Built-in roles cannot be deleted.");
        }

        long assignedUsers = userAccountRepository.countByRole_Id(roleId);
        if (assignedUsers > 0) {
            throw conflict(
                    "ROLE_ASSIGNED",
                    "This role is assigned to one or more users and cannot be deleted."
            );
        }

        roleRepository.delete(role);
    }

    private void assertRoleListAccess(AuthenticatedUser actor, UUID tenantId) {
        if (tenantId.equals(actor.tenantId())) {
            return;
        }
        if (!actor.isOwner()) {
            throw forbidden("ROLE_ACCESS_DENIED", "Only Owner may view another tenant's roles.");
        }
        UserAccount current = userAccountRepository
                .findByIdAndTenant_Id(actor.userId(), actor.tenantId())
                .orElseThrow(() -> forbidden("ROLE_ACCESS_DENIED", "Current user is unavailable."));
        userAccountRepository
                .findByTenant_IdAndIdentity_Id(tenantId, current.getIdentity().getId())
                .filter(UserAccount::isActive)
                .filter(user -> user.getRole().isActive())
                .filter(user -> user.getRole().getSystemKey() == SystemRole.OWNER)
                .orElseThrow(() -> forbidden(
                        "ROLE_ACCESS_DENIED",
                        "This tenant is not assigned to the current Owner login."
                ));
    }

    private Role findRole(UUID tenantId, UUID roleId) {
        return roleRepository.findByIdAndTenant_Id(roleId, tenantId)
                .orElseThrow(() -> notFound("ROLE_NOT_FOUND", "Role not found."));
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
