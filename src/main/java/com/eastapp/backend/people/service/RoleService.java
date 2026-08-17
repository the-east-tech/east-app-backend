package com.eastapp.backend.people.service;

import com.eastapp.backend.auth.security.AuthenticatedUser;
import com.eastapp.backend.people.Role;
import com.eastapp.backend.people.RoleRepository;
import com.eastapp.backend.people.UserAccountRepository;
import com.eastapp.backend.people.api.RoleResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class RoleService {

    private final RoleRepository roleRepository;
    private final UserAccountRepository userAccountRepository;

    public RoleService(RoleRepository roleRepository, UserAccountRepository userAccountRepository) {
        this.roleRepository = roleRepository;
        this.userAccountRepository = userAccountRepository;
    }

    @Transactional(readOnly = true)
    public List<RoleResponse> list(AuthenticatedUser actor) {
        UUID tenantId = actor.tenantId();
        Map<UUID, Long> counts = new HashMap<>();
        for (Object[] row : userAccountRepository.countUsersByRoleForTenant(tenantId)) {
            counts.put((UUID) row[0], (Long) row[1]);
        }
        return roleRepository.findAllByTenant_IdOrderByNameAsc(tenantId)
                .stream()
                .filter(Role::isActive)
                .filter(role -> actor.systemRole().canView(role.getSystemKey()))
                .sorted((left, right) -> Integer.compare(left.getSystemKey().rank(), right.getSystemKey().rank()))
                .map(role -> RoleResponse.from(role, counts.getOrDefault(role.getId(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RoleResponse> assignable(AuthenticatedUser actor) {
        return roleRepository.findAllByTenant_IdOrderByNameAsc(actor.tenantId())
                .stream()
                .filter(Role::isActive)
                .filter(role -> actor.systemRole().canAssign(role.getSystemKey()))
                .sorted((left, right) -> Integer.compare(left.getSystemKey().rank(), right.getSystemKey().rank()))
                .map(role -> RoleResponse.from(role, 0))
                .toList();
    }
}
