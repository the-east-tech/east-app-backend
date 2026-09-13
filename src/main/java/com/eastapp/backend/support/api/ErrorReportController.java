package com.eastapp.backend.support.api;

import com.eastapp.backend.auth.security.AuthenticatedUser;
import com.eastapp.backend.support.service.ErrorReportService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/support/error-reports")
public class ErrorReportController {

    private final ErrorReportService errorReportService;

    public ErrorReportController(ErrorReportService errorReportService) {
        this.errorReportService = errorReportService;
    }

    @PostMapping
    ResponseEntity<Void> report(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody ErrorReportRequest request
    ) {
        errorReportService.sendUserReport(principal, request);
        return ResponseEntity.noContent().build();
    }
}
