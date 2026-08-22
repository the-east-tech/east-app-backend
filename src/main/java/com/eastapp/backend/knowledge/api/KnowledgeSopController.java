package com.eastapp.backend.knowledge.api;

import com.eastapp.backend.auth.security.AuthenticatedUser;
import com.eastapp.backend.common.api.PageResponse;
import com.eastapp.backend.knowledge.service.KnowledgeSopService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/knowledge/sops")
public class KnowledgeSopController {
    private final KnowledgeSopService sopService;

    public KnowledgeSopController(KnowledgeSopService sopService) {
        this.sopService = sopService;
    }

    @GetMapping
    PageResponse<KnowledgeSopResponse> list(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(required = false) UUID tagId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return sopService.list(principal, search, tagId, page, size);
    }

    @GetMapping("/{sopId}")
    KnowledgeSopResponse get(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID sopId
    ) {
        return sopService.get(principal, sopId);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER', 'HEAD', 'MANAGER')")
    ResponseEntity<KnowledgeSopResponse> create(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody CreateKnowledgeSopRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(sopService.create(principal, request));
    }

    @PutMapping("/{sopId}")
    @PreAuthorize("hasAnyRole('OWNER', 'HEAD', 'MANAGER')")
    KnowledgeSopResponse update(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID sopId,
            @Valid @RequestBody UpdateKnowledgeSopRequest request
    ) {
        return sopService.update(principal, sopId, request);
    }

    @PostMapping("/bulk-delete")
    @PreAuthorize("hasAnyRole('OWNER', 'HEAD', 'MANAGER')")
    ResponseEntity<Void> bulkDelete(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody BulkDeleteKnowledgeSopsRequest request
    ) {
        sopService.bulkDelete(principal, request);
        return ResponseEntity.noContent().build();
    }
}
