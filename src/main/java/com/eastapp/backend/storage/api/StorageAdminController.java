package com.eastapp.backend.storage.api;

import com.eastapp.backend.auth.security.AuthenticatedUser;
import com.eastapp.backend.storage.service.StorageAdminService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/storage-admin")
public class StorageAdminController {
    private final StorageAdminService storageAdminService;

    public StorageAdminController(StorageAdminService storageAdminService) {
        this.storageAdminService = storageAdminService;
    }

    @GetMapping
    StorageOverviewResponse overview(
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        return storageAdminService.overview(principal);
    }

    @GetMapping("/tables/{key}")
    StorageTableDataResponse tableData(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable String key,
            @RequestParam int rowCount,
            @RequestParam(defaultValue = "false") boolean latestFirst
    ) {
        return storageAdminService.tableData(principal, key, rowCount, latestFirst);
    }

    @PostMapping("/cleanup/{key}")
    StorageCleanupResponse cleanup(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable String key,
            @Valid @RequestBody StorageCleanupRequest request
    ) {
        return storageAdminService.cleanup(
                principal,
                key,
                request.confirmed(),
                request.rowCount()
        );
    }
}
