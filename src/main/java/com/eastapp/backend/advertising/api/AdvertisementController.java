package com.eastapp.backend.advertising.api;

import com.eastapp.backend.activity.tracking.ActivityTracked;
import com.eastapp.backend.advertising.service.AdvertisementService;
import com.eastapp.backend.auth.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/advertisements")
public class AdvertisementController {
    private final AdvertisementService service;

    public AdvertisementController(AdvertisementService service) {
        this.service = service;
    }

    @GetMapping("/active")
    public ActiveAdvertisementFeedResponse active(
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        return service.active(principal);
    }

    @GetMapping
    @PreAuthorize("hasRole('OWNER')")
    public List<AdvertisementResponse> all(
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        return service.all(principal);
    }

    @ActivityTracked(module = "Advertising", action = "created", entity = "advertisement")
    @PostMapping
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<AdvertisementResponse> create(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody UpsertAdvertisementRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.create(principal, request));
    }

    @ActivityTracked(module = "Advertising", action = "updated", entity = "advertisement", targetPathVariable = "id")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('OWNER')")
    public AdvertisementResponse update(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id,
            @Valid @RequestBody UpsertAdvertisementRequest request
    ) {
        return service.update(principal, id, request);
    }

    @ActivityTracked(module = "Advertising", action = "deleted", entity = "advertisement", targetPathVariable = "id")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id
    ) {
        service.delete(principal, id);
        return ResponseEntity.noContent().build();
    }
}
