package com.eastapp.backend.tasks.api;

import com.eastapp.backend.auth.security.AuthenticatedUser;
import com.eastapp.backend.tasks.DailyTaskStatus;
import com.eastapp.backend.tasks.service.DailyTaskService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/daily-tasks")
public class DailyTaskController {
    private final DailyTaskService service;

    public DailyTaskController(DailyTaskService service) {
        this.service = service;
    }

    @GetMapping("/overview")
    DailyTaskOverviewResponse overview(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return service.overview(principal, date);
    }

    @GetMapping("/templates")
    @PreAuthorize("hasAnyRole('OWNER', 'HEAD', 'MANAGER')")
    List<DailyTaskTemplateResponse> templates(
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        return service.templates(principal);
    }

    @PostMapping("/templates")
    @PreAuthorize("hasAnyRole('OWNER', 'HEAD', 'MANAGER')")
    ResponseEntity<DailyTaskTemplateResponse> createTemplate(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody UpsertDailyTaskTemplateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.createTemplate(principal, request));
    }

    @PatchMapping("/templates/{templateId}")
    @PreAuthorize("hasAnyRole('OWNER', 'HEAD', 'MANAGER')")
    DailyTaskTemplateResponse updateTemplate(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID templateId,
            @Valid @RequestBody UpsertDailyTaskTemplateRequest request
    ) {
        return service.updateTemplate(principal, templateId, request);
    }

    @GetMapping("/records")
    DailyTaskListResponse records(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) UUID tagId,
            @RequestParam(required = false) DailyTaskStatus status,
            @RequestParam(defaultValue = "false") boolean submittedByMe
    ) {
        return service.records(
                principal, date, dateFrom, dateTo, tagId, status, submittedByMe
        );
    }

    @GetMapping("/records/{recordId}")
    DailyTaskRecordResponse record(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID recordId
    ) {
        return service.record(principal, recordId);
    }

    @PostMapping(
            value = "/records/{recordId}/submit",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    DailyTaskRecordResponse submit(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID recordId,
            @RequestParam(defaultValue = "") String completedChecklistItemIds,
            @RequestPart(value = "photos", required = false) List<MultipartFile> photos
    ) {
        return service.submit(
                principal,
                recordId,
                completedChecklistItemIds,
                photos == null ? List.of() : photos
        );
    }

    @PostMapping("/records/{recordId}/rate")
    @PreAuthorize("hasAnyRole('OWNER', 'HEAD', 'MANAGER')")
    DailyTaskRecordResponse rate(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID recordId,
            @Valid @RequestBody RateDailyTaskRequest request
    ) {
        return service.rate(principal, recordId, request);
    }
}
