package com.eastapp.backend.tasks.api;

import com.eastapp.backend.auth.security.AuthenticatedUser;
import com.eastapp.backend.tasks.TaskStatus;
import com.eastapp.backend.tasks.service.TaskService;
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
@RequestMapping("/api/v1/tasks")
public class TaskController {
    private final TaskService service;

    public TaskController(TaskService service) {
        this.service = service;
    }

    @GetMapping("/overview")
    @PreAuthorize("hasAuthority('PERMISSION_TASK_VIEW')")
    TaskOverviewResponse overview(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return service.overview(principal, date);
    }

    @GetMapping("/templates")
    @PreAuthorize("hasAuthority('PERMISSION_TASK_MANAGE')")
    List<TaskTemplateResponse> templates(
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        return service.templates(principal);
    }

    @PostMapping("/templates")
    @PreAuthorize("hasAuthority('PERMISSION_TASK_MANAGE')")
    ResponseEntity<TaskTemplateResponse> createTemplate(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody UpsertTaskTemplateRequest request
    ) {
        TaskTemplateResponse created = service.createTemplate(principal, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(created);
    }

    @PatchMapping("/templates/{templateId}")
    @PreAuthorize("hasAuthority('PERMISSION_TASK_MANAGE')")
    TaskTemplateResponse updateTemplate(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID templateId,
            @Valid @RequestBody UpsertTaskTemplateRequest request
    ) {
        return service.updateTemplate(
                principal,
                templateId,
                request
        );
    }

    @GetMapping("/records")
    @PreAuthorize("hasAuthority('PERMISSION_TASK_VIEW')")
    TaskListResponse records(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) UUID tagId,
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(required = false) List<TaskStatus> statuses,
            @RequestParam(defaultValue = "false") boolean submittedByMe,
            @RequestParam(defaultValue = "false") boolean upcoming,
            @RequestParam(defaultValue = "3") int limit
    ) {
        if (upcoming) {
            return service.upcomingRecords(principal, tagId, limit);
        }
        return service.records(
                principal, date, dateFrom, dateTo, tagId, status, statuses, submittedByMe
        );
    }

    @GetMapping("/approvals")
    @PreAuthorize("hasAuthority('PERMISSION_TASK_RATE')")
    TaskListResponse approvals(
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        return service.approvals(principal);
    }

    @GetMapping("/records/{recordId}")
    @PreAuthorize("hasAuthority('PERMISSION_TASK_VIEW')")
    TaskRecordResponse record(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID recordId
    ) {
        return service.record(principal, recordId);
    }

    @PostMapping(
            value = "/records/{recordId}/submit",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @PreAuthorize("hasAuthority('PERMISSION_TASK_CONTRIBUTE')")
    TaskRecordResponse submit(
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
    @PreAuthorize("hasAuthority('PERMISSION_TASK_RATE')")
    TaskRecordResponse rate(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID recordId,
            @Valid @RequestBody RateTaskRequest request
    ) {
        return service.rate(principal, recordId, request);
    }
}
