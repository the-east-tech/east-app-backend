package com.eastapp.backend.people.api;

import com.eastapp.backend.auth.security.AuthenticatedUser;
import com.eastapp.backend.people.service.RoleService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/roles")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('HEAD', 'MANAGER')")
    List<RoleResponse> list(@AuthenticationPrincipal AuthenticatedUser principal) {
        return roleService.list(principal.tenantId());
    }

    @PostMapping
    @PreAuthorize("hasRole('HEAD')")
    ResponseEntity<RoleResponse> create(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody CreateRoleRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(roleService.create(principal.tenantId(), request));
    }

    @PatchMapping("/{roleId}")
    @PreAuthorize("hasRole('HEAD')")
    RoleResponse update(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID roleId,
            @Valid @RequestBody UpdateRoleRequest request
    ) {
        return roleService.update(principal.tenantId(), roleId, request);
    }

    @DeleteMapping("/{roleId}")
    @PreAuthorize("hasRole('HEAD')")
    ResponseEntity<Void> delete(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID roleId
    ) {
        roleService.delete(principal.tenantId(), roleId);
        return ResponseEntity.noContent().build();
    }
}
