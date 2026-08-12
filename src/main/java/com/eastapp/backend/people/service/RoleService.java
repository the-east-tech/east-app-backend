package com.eastapp.backend.people.service;

import com.eastapp.backend.auth.security.AuthenticatedUser;
import com.eastapp.backend.common.error.ApiException;
import com.eastapp.backend.organisation.Tenant;
import com.eastapp.backend.organisation.TenantRepository;
import com.eastapp.backend.people.Role;
import com.eastapp.backend.people.RoleRepository;
import com.eastapp.backend.people.SystemRole;
import com.eastapp.backend.people.UserAccountRepository;
import com.eastapp.backend.people.api.CreateRoleRequest;
import com.eastapp.backend.people.api.RoleResponse;
import com.eastapp.backend.people.api.UpdateRoleRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    /** Role management list for the active tenant, including usage counts. */
    @Transactional(readOnly = true)
    public List<RoleResponse> list(AuthenticatedUser actor) {
        UUID tenantId = actor.tenantId();
        Map<UUID, Long> counts = new HashMap<>();
        for (Object[] row : userAccountRepository.countUsersByRoleForTenant(tenantId)) {
            counts.put((UUID) row[0], (Long) row[1]);
        }
        return roleRepository.findAllByTenant_IdOrderByNameAsc(tenantId)
                .stream()
                .map(role -> RoleResponse.from(role, counts.getOrDefault(role.getId(), 0L)))
                .toList();
    }

    /** Lightweight role dropdown for Create User; no usage/count queries. */
    @Transactional(readOnly = true)
    public List<RoleResponse> assignable(AuthenticatedUser actor) {
        return roleRepository.findAllByTenant_IdOrderByNameAsc(actor.tenantId())
                .stream()
                .filter(Role::isActive)
                .filter(role -> mayAssign(actor, role))
                .map(role -> RoleResponse.from(role, 0))
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

    private static boolean mayAssign(AuthenticatedUser actor, Role role) {
        if (actor.isOwner()) return true;
        if (actor.systemRole() == SystemRole.HEAD) {
            return role.getSystemKey() != SystemRole.OWNER;
        }
        if (actor.isManager()) {
            return role.getSystemKey() == SystemRole.STAFF_1
                    || role.getSystemKey() == SystemRole.STAFF_2;
        }
        return false;
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
}
