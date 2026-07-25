package com.eastapp.backend.auth.api;

import com.eastapp.backend.auth.security.AuthenticatedUser;
import com.eastapp.backend.auth.service.AuthenticationService;
import jakarta.validation.Valid;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthenticationController {

    private final AuthenticationService authenticationService;

    public AuthenticationController(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @PostMapping("/login")
    LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authenticationService.login(request);
    }

    @GetMapping("/me")
    CurrentUserResponse me(@AuthenticationPrincipal AuthenticatedUser principal) {
        return authenticationService.currentUser(principal);
    }

    @GetMapping("/contexts")
    List<CurrentUserResponse> contexts(
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        return authenticationService.contexts(principal);
    }

    @PostMapping("/context")
    CurrentUserResponse switchContext(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody SwitchContextRequest request
    ) {
        return authenticationService.switchContext(principal, request.userId());
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(@AuthenticationPrincipal AuthenticatedUser principal) {
        authenticationService.logout(principal.sessionId());
        return ResponseEntity.noContent().build();
    }
}
