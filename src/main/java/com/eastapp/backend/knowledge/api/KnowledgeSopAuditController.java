package com.eastapp.backend.knowledge.api;

import com.eastapp.backend.auth.security.AuthenticatedUser;
import com.eastapp.backend.knowledge.service.KnowledgeSopAuditService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/knowledge")
public class KnowledgeSopAuditController {
    private static final String AUDIT_PERMISSION =
            "hasAuthority('PERMISSION_KNOWLEDGE_AUDIT_VIEW')";

    private final KnowledgeSopAuditService auditService;

    public KnowledgeSopAuditController(KnowledgeSopAuditService auditService) {
        this.auditService = auditService;
    }

    @PostMapping("/sops/{sopId}/watch-time")
    ResponseEntity<Void> record(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID sopId,
            @Valid @RequestBody RecordSopWatchTimeRequest request
    ) {
        auditService.record(principal, sopId, request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/audit/users/{userId}")
    @PreAuthorize(AUDIT_PERMISSION)
    UserSopAuditResponse forUser(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID userId
    ) {
        return auditService.forUser(principal, userId);
    }

    @GetMapping("/audit/sops")
    @PreAuthorize(AUDIT_PERMISSION)
    SopImpactAuditResponse impact(
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        return auditService.impact(principal);
    }
}
