package com.eastapp.backend.tasks.api;

import com.eastapp.backend.activity.tracking.ActivityTracked;
import com.eastapp.backend.activity.tracking.ActivityEventContext;
import com.eastapp.backend.auth.security.AuthenticatedUser;
import com.eastapp.backend.tasks.TaskStatus;
import com.eastapp.backend.tasks.service.TaskService;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
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

    @ActivityTracked(module = "Task", action = "created", entity = "task template")
    @PostMapping("/templates")
    @PreAuthorize("hasAuthority('PERMISSION_TASK_MANAGE')")
    ResponseEntity<TaskTemplateResponse> createTemplate(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody UpsertTaskTemplateRequest request,
            HttpServletRequest httpRequest
    ) {
        TaskTemplateResponse created = service.createTemplate(principal, request);
        ActivityEventContext.attach(
                httpRequest,
                created.id(),
                created.title(),
                templateDetail(created)
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(created);
    }

    @ActivityTracked(module = "Task", action = "updated", entity = "task template", targetPathVariable = "templateId")
    @PatchMapping("/templates/{templateId}")
    @PreAuthorize("hasAuthority('PERMISSION_TASK_MANAGE')")
    TaskTemplateResponse updateTemplate(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID templateId,
            @Valid @RequestBody UpsertTaskTemplateRequest request,
            HttpServletRequest httpRequest
    ) {
        TaskTemplateResponse updated = service.updateTemplate(
                principal,
                templateId,
                request
        );
        ActivityEventContext.attach(
                httpRequest,
                updated.id(),
                updated.title(),
                templateDetail(updated)
        );
        return updated;
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
            @RequestParam(defaultValue = "false") boolean submittedByMe
    ) {
        return service.records(
                principal, date, dateFrom, dateTo, tagId, status, submittedByMe
        );
    }

    @GetMapping("/records/{recordId}")
    @PreAuthorize("hasAuthority('PERMISSION_TASK_VIEW')")
    TaskRecordResponse record(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID recordId
    ) {
        return service.record(principal, recordId);
    }

    @ActivityTracked(module = "Task", action = "submitted", entity = "task", targetPathVariable = "recordId")
    @PostMapping(
            value = "/records/{recordId}/submit",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @PreAuthorize("hasAuthority('PERMISSION_TASK_CONTRIBUTE')")
    TaskRecordResponse submit(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID recordId,
            @RequestParam(defaultValue = "") String completedChecklistItemIds,
            @RequestPart(value = "photos", required = false) List<MultipartFile> photos,
            HttpServletRequest httpRequest
    ) {
        TaskRecordResponse submitted = service.submit(
                principal,
                recordId,
                completedChecklistItemIds,
                photos == null ? List.of() : photos
        );
        ActivityEventContext.attach(
                httpRequest,
                submitted.id(),
                submitted.title(),
                submissionDetail(submitted)
        );
        return submitted;
    }

    @ActivityTracked(module = "Task", action = "rated", entity = "task", targetPathVariable = "recordId")
    @PostMapping("/records/{recordId}/rate")
    @PreAuthorize("hasAuthority('PERMISSION_TASK_RATE')")
    TaskRecordResponse rate(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID recordId,
            @Valid @RequestBody RateTaskRequest request,
            HttpServletRequest httpRequest
    ) {
        TaskRecordResponse rated = service.rate(principal, recordId, request);
        ActivityEventContext.attach(
                httpRequest,
                rated.id(),
                rated.title(),
                ratingDetail(rated)
        );
        return rated;
    }

    private static String templateDetail(TaskTemplateResponse template) {
        return "Tag: " + template.tagName()
                + "\nSchedule: " + template.scheduleType()
                + "\nFirst task date: " + template.firstTaskDate()
                + "\nEnd date: " + (template.endDate() == null ? "None" : template.endDate())
                + "\nRequired photos: " + template.requiredPhotoCount()
                + "\nChecklist items: " + template.checklistItems().size()
                + "\nLinked SOP: "
                + (template.linkedSopTitle() == null ? "None" : template.linkedSopTitle())
                + "\nActive: " + (template.active() ? "Yes" : "No");
    }

    private static String submissionDetail(TaskRecordResponse record) {
        long completedChecklist = record.checklistItems().stream()
                .filter(TaskChecklistItemResponse::completed)
                .count();
        String submittedBy = record.submittedBy() == null
                ? "-"
                : record.submittedBy().fullName()
                        + " (" + record.submittedBy().employeeId() + ")";
        return "Task date: " + record.taskDate()
                + "\nTag: " + record.tagName()
                + "\nSubmitted by: " + submittedBy
                + "\nPhotos: " + record.photoCount() + "/" + record.requiredPhotoCount()
                + "\nChecklist: " + completedChecklist + "/" + record.checklistItems().size();
    }

    private static String ratingDetail(TaskRecordResponse record) {
        String submittedBy = record.submittedBy() == null
                ? "-"
                : record.submittedBy().fullName()
                        + " (" + record.submittedBy().employeeId() + ")";
        return "Task date: " + record.taskDate()
                + "\nEmployee: " + submittedBy
                + "\nRating: " + record.rating() + "/5"
                + "\nComment: " + record.ratingComment();
    }
}
