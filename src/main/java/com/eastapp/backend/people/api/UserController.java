package com.eastapp.backend.people.api;

import com.eastapp.backend.activity.tracking.ActivityTracked;
import com.eastapp.backend.auth.security.AuthenticatedUser;
import com.eastapp.backend.common.api.PageResponse;
import com.eastapp.backend.people.SystemRole;
import com.eastapp.backend.people.service.UserAccountService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@PreAuthorize("hasAnyRole('OWNER', 'HEAD', 'MANAGER')")
public class UserController {

    private final UserAccountService userAccountService;

    public UserController(UserAccountService userAccountService) {
        this.userAccountService = userAccountService;
    }

    @GetMapping
    PageResponse<UserResponse> list(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) SystemRole role,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return userAccountService.list(principal, search, active, role, page, size);
    }

    @GetMapping("/{userId}")
    UserResponse get(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID userId
    ) {
        return userAccountService.get(principal, userId);
    }

    @ActivityTracked(module = "People", action = "created", entity = "employee")
    @PostMapping
    ResponseEntity<UserResponse> create(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody CreateUserRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(userAccountService.create(principal, request));
    }

    @ActivityTracked(module = "People", action = "updated", entity = "employee", targetPathVariable = "userId")
    @PatchMapping("/{userId}")
    UserResponse update(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateUserRequest request
    ) {
        return userAccountService.update(principal, userId, request);
    }

    @ActivityTracked(module = "People", action = "reset", entity = "employee password", targetPathVariable = "userId")
    @PutMapping("/{userId}/password")
    ResponseEntity<Void> resetPassword(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID userId,
            @Valid @RequestBody ResetPasswordRequest request
    ) {
        userAccountService.resetPassword(principal, userId, request);
        return ResponseEntity.noContent().build();
    }
}
