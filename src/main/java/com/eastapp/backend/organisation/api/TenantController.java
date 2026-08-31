package com.eastapp.backend.organisation.api;

import com.eastapp.backend.activity.tracking.ActivityTracked;
import com.eastapp.backend.auth.security.AuthenticatedUser;
import com.eastapp.backend.organisation.service.TenantService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
@RequestMapping("/api/v1/tenants")
@PreAuthorize("hasRole('OWNER')")
public class TenantController {

    private final TenantService tenantService;

    public TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @GetMapping
    List<TenantResponse> list(@AuthenticationPrincipal AuthenticatedUser principal) {
        return tenantService.list(principal);
    }

    @ActivityTracked(module = "Business", action = "created", entity = "business")
    @PostMapping
    @PreAuthorize("hasRole('OWNER')")
    ResponseEntity<TenantResponse> create(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody CreateTenantRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(tenantService.create(principal, request));
    }

    @ActivityTracked(module = "Business", action = "updated", entity = "business", targetPathVariable = "tenantId")
    @PatchMapping("/{tenantId}")
    TenantResponse update(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID tenantId,
            @Valid @RequestBody UpdateTenantRequest request
    ) {
        return tenantService.update(principal, tenantId, request);
    }
}
