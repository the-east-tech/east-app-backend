package com.eastapp.backend.people.api;

import com.eastapp.backend.auth.security.AuthenticatedUser;
import com.eastapp.backend.people.service.RoleService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/roles")
@PreAuthorize("hasAnyRole('OWNER', 'HEAD', 'MANAGER')")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping
    List<RoleResponse> list(@AuthenticationPrincipal AuthenticatedUser principal) {
        return roleService.list(principal);
    }

    @GetMapping("/assignable")
    List<RoleResponse> assignable(@AuthenticationPrincipal AuthenticatedUser principal) {
        return roleService.assignable(principal);
    }
}
