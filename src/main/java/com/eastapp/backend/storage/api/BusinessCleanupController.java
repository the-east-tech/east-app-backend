package com.eastapp.backend.storage.api;

import com.eastapp.backend.auth.security.AuthenticatedUser;
import com.eastapp.backend.storage.service.BusinessCleanupService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/business-cleanup")
@PreAuthorize("hasRole('OWNER')")
public class BusinessCleanupController {
    private static final MediaType ZIP = MediaType.parseMediaType("application/zip");

    private final BusinessCleanupService businessCleanupService;

    public BusinessCleanupController(BusinessCleanupService businessCleanupService) {
        this.businessCleanupService = businessCleanupService;
    }

    @PostMapping("/preview")
    BusinessCleanupPreviewResponse preview(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody BusinessCleanupRequest request
    ) {
        return businessCleanupService.preview(principal, request);
    }

    @PostMapping(value = "/backups", produces = "application/zip")
    ResponseEntity<byte[]> createBackup(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody BusinessCleanupRequest request
    ) {
        BusinessCleanupService.BackupFile backup = businessCleanupService.createBackup(principal, request);
        return ResponseEntity.ok()
                .contentType(ZIP)
                .contentLength(backup.bytes().length)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + backup.fileName() + "\"")
                .header("X-EastApp-Cleanup-Run-Id", backup.runId().toString())
                .header("X-EastApp-Backup-SHA256", backup.sha256())
                .body(backup.bytes());
    }

    @GetMapping("/runs")
    List<BusinessCleanupRunResponse> runs(
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        return businessCleanupService.runs(principal);
    }

    @PostMapping("/runs/{runId}/confirm-saved")
    BusinessCleanupRunResponse confirmSaved(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID runId
    ) {
        return businessCleanupService.confirmSaved(principal, runId);
    }

    @DeleteMapping("/runs/{runId}")
    BusinessCleanupCompleteResponse cleanup(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID runId
    ) {
        return businessCleanupService.cleanup(principal, runId);
    }
}
