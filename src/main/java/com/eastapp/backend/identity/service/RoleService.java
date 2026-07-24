package com.eastapp.backend.identity.service;

import com.eastapp.backend.identity.Role;
import com.eastapp.backend.identity.RoleRepository;
import com.eastapp.backend.identity.SystemRole;
import com.eastapp.backend.identity.Tenant;
import com.eastapp.backend.identity.TenantRepository;
import com.eastapp.backend.identity.UserAccountRepository;
import com.eastapp.backend.identity.api.CreateRoleRequest;
import com.eastapp.backend.identity.api.RoleResponse;
import com.eastapp.backend.identity.api.UpdateRoleRequest;
import com.eastapp.backend.identity.support.ApiException;
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
    public List<RoleResponse> list(UUID tenantId) {
        return roleRepository.findAllByTenant_IdOrderByNameAsc(tenantId)
                .stream()
                .map(role -> RoleResponse.from(
                        role,
                        userAccountRepository.countByRole_Id(role.getId())
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
                tenantId,
                request.name().trim(),
                roleId
        )) {
            throw conflict("ROLE_NAME_EXISTS", "A role with this name already exists.");
        }

        if (role.getSystemKey() == SystemRole.HEAD && !request.active()) {
            throw conflict("HEAD_ROLE_REQUIRED", "The Head role cannot be deactivated.");
        }

        role.rename(request.name());
        if (request.active()) {
            role.activate();
        } else {
            role.deactivate();
        }

        return RoleResponse.from(
                role,
                userAccountRepository.countByRole_Id(role.getId())
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
