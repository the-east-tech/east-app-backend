package com.eastapp.backend.tasks.service;

import com.eastapp.backend.auth.security.AuthenticatedUser;
import com.eastapp.backend.auth.permission.SystemPermission;
import com.eastapp.backend.common.error.ApiException;
import com.eastapp.backend.knowledge.KnowledgeSop;
import com.eastapp.backend.knowledge.KnowledgeSopRepository;
import com.eastapp.backend.organisation.TenantRepository;
import com.eastapp.backend.people.SystemRole;
import com.eastapp.backend.people.UserAccount;
import com.eastapp.backend.people.UserAccountRepository;
import com.eastapp.backend.reports.ReportMedia;
import com.eastapp.backend.reports.ReportMediaRepository;
import com.eastapp.backend.reports.ReportMediaReference;
import com.eastapp.backend.reports.config.ReportProperties;
import com.eastapp.backend.reports.service.ReportMediaService;
import com.eastapp.backend.stock.StockTag;
import com.eastapp.backend.stock.StockTagAssigneeRepository;
import com.eastapp.backend.stock.StockTagRepository;
import com.eastapp.backend.tasks.TaskAuditEntry;
import com.eastapp.backend.tasks.TaskAuditEntryRepository;
import com.eastapp.backend.tasks.TaskPhoto;
import com.eastapp.backend.tasks.TaskPhotoRepository;
import com.eastapp.backend.tasks.TaskRecord;
import com.eastapp.backend.tasks.TaskRecordChecklistItem;
import com.eastapp.backend.tasks.TaskRecordChecklistItemRepository;
import com.eastapp.backend.tasks.TaskRecordRepository;
import com.eastapp.backend.tasks.TaskScheduleType;
import com.eastapp.backend.tasks.TaskStatus;
import com.eastapp.backend.tasks.TaskTemplate;
import com.eastapp.backend.tasks.TaskTemplateChecklistItem;
import com.eastapp.backend.tasks.TaskTemplateChecklistItemRepository;
import com.eastapp.backend.tasks.TaskTemplateRepository;
import com.eastapp.backend.tasks.api.TaskAuditResponse;
import com.eastapp.backend.tasks.api.TaskChecklistItemResponse;
import com.eastapp.backend.tasks.api.TaskListResponse;
import com.eastapp.backend.tasks.api.TaskOverviewResponse;
import com.eastapp.backend.tasks.api.TaskPersonResponse;
import com.eastapp.backend.tasks.api.TaskPhotoResponse;
import com.eastapp.backend.tasks.api.TaskRecordResponse;
import com.eastapp.backend.tasks.api.TaskTemplateResponse;
import com.eastapp.backend.tasks.api.RateTaskRequest;
import com.eastapp.backend.tasks.api.UpsertTaskTemplateRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class TaskService {
    private static final int MAX_CONFIRMED_PHOTOS_PER_TASK = 40;
    private static final int MAX_HISTORY_RANGE_DAYS = 30;
    private static final int ACTIVE_TASK_FUTURE_DAYS = 10;

    private final TaskTemplateRepository templateRepository;
    private final TaskTemplateChecklistItemRepository templateChecklistRepository;
    private final TaskRecordRepository recordRepository;
    private final TaskRecordChecklistItemRepository recordChecklistRepository;
    private final TaskPhotoRepository photoRepository;
    private final TaskAuditEntryRepository auditRepository;
    private final KnowledgeSopRepository knowledgeSopRepository;
    private final StockTagRepository tagRepository;
    private final StockTagAssigneeRepository tagAssigneeRepository;
    private final UserAccountRepository userRepository;
    private final TenantRepository tenantRepository;
    private final ReportMediaRepository mediaRepository;
    private final ReportMediaService mediaService;
    private final ReportProperties reportProperties;
    private final TaskAccessPolicy accessPolicy;

    public TaskService(
            TaskTemplateRepository templateRepository,
            TaskTemplateChecklistItemRepository templateChecklistRepository,
            TaskRecordRepository recordRepository,
            TaskRecordChecklistItemRepository recordChecklistRepository,
            TaskPhotoRepository photoRepository,
            TaskAuditEntryRepository auditRepository,
            KnowledgeSopRepository knowledgeSopRepository,
            StockTagRepository tagRepository,
            StockTagAssigneeRepository tagAssigneeRepository,
            UserAccountRepository userRepository,
            TenantRepository tenantRepository,
            ReportMediaRepository mediaRepository,
            ReportMediaService mediaService,
            ReportProperties reportProperties,
            TaskAccessPolicy accessPolicy
    ) {
        this.templateRepository = templateRepository;
        this.templateChecklistRepository = templateChecklistRepository;
        this.recordRepository = recordRepository;
        this.recordChecklistRepository = recordChecklistRepository;
        this.photoRepository = photoRepository;
        this.auditRepository = auditRepository;
        this.knowledgeSopRepository = knowledgeSopRepository;
        this.tagRepository = tagRepository;
        this.tagAssigneeRepository = tagAssigneeRepository;
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.mediaRepository = mediaRepository;
        this.mediaService = mediaService;
        this.reportProperties = reportProperties;
        this.accessPolicy = accessPolicy;
    }

    public List<TaskTemplateResponse> templates(AuthenticatedUser principal) {
        requireManagement(principal);
        List<TaskTemplate> templates = templateRepository
                .findAllByTenantIdOrderByActiveDescTitleAsc(principal.tenantId());
        Map<UUID, String> linkedSopTitles = linkedSopTitles(
                principal.tenantId(),
                templates.stream()
                        .map(TaskTemplate::getLinkedSopId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet())
        );
        return templates.stream()
                .map(template -> toTemplateResponse(
                        principal.tenantId(),
                        template,
                        linkedSopTitle(linkedSopTitles, template.getLinkedSopId())
                ))
                .toList();
    }

    @Transactional
    public TaskTemplateResponse createTemplate(
            AuthenticatedUser principal,
            UpsertTaskTemplateRequest request
    ) {
        requireManagement(principal);
        validateTemplateSchedule(request);
        StockTag tag = requireTag(principal.tenantId(), request.tagId());
        KnowledgeSop linkedSop = requireLinkedSopOrNull(
                principal.tenantId(), request.linkedSopId()
        );
        TaskTemplate template = templateRepository.saveAndFlush(new TaskTemplate(
                principal.tenantId(),
                tag.getId(),
                linkedSop == null ? null : linkedSop.getId(),
                request.title(),
                request.instruction(),
                request.requiredPhotoCount(),
                request.scheduleType(),
                request.firstTaskDate(),
                request.endDate(),
                request.active(),
                principal.userId()
        ));
        saveTemplateChecklist(principal.tenantId(), template.getId(), request.checklistItems());
        if (template.isActive() && template.isScheduledFor(today())) {
            lockTenant(principal.tenantId());
            materialiseTemplate(template, today(), tag);
        }
        return toTemplateResponse(
                principal.tenantId(),
                template,
                linkedSop == null ? null : linkedSop.getTitle()
        );
    }

    @Transactional
    public TaskTemplateResponse updateTemplate(
            AuthenticatedUser principal,
            UUID templateId,
            UpsertTaskTemplateRequest request
    ) {
        requireManagement(principal);
        validateTemplateSchedule(request);
        TaskTemplate template = requireTemplate(principal.tenantId(), templateId);
        StockTag previousTag = requireTag(principal.tenantId(), template.getTagId());
        // Freeze today's version before changing the template. Edits affect the
        // next business date, while a newly-created template starts today.
        if (template.isActive() && template.isScheduledFor(today())) {
            lockTenant(principal.tenantId());
            materialiseTemplate(template, today(), previousTag);
        }

        StockTag nextTag = requireTag(principal.tenantId(), request.tagId());
        KnowledgeSop nextLinkedSop = requireLinkedSopOrNull(
                principal.tenantId(), request.linkedSopId()
        );
        template.update(
                nextTag.getId(),
                nextLinkedSop == null ? null : nextLinkedSop.getId(),
                request.title(),
                request.instruction(),
                request.requiredPhotoCount(),
                request.scheduleType(),
                request.firstTaskDate(),
                request.endDate(),
                request.active(),
                principal.userId()
        );
        templateChecklistRepository.deleteAllByTenantIdAndTemplateId(
                principal.tenantId(), templateId
        );
        templateChecklistRepository.flush();
        saveTemplateChecklist(principal.tenantId(), templateId, request.checklistItems());
        return toTemplateResponse(
                principal.tenantId(),
                template,
                nextLinkedSop == null ? null : nextLinkedSop.getTitle()
        );
    }

    @Transactional
    public TaskListResponse records(
            AuthenticatedUser principal,
            LocalDate requestedDate,
            LocalDate requestedFrom,
            LocalDate requestedTo,
            UUID tagId,
            TaskStatus status,
            List<TaskStatus> statuses,
            boolean submittedByMe
    ) {
        requireTaskView(principal);
        LocalDate today = today();
        LocalDate dateFrom;
        LocalDate dateTo;
        if (requestedDate != null) {
            dateFrom = requestedDate;
            dateTo = requestedDate;
        } else {
            dateFrom = requestedFrom == null
                    ? requestedTo == null ? today : requestedTo
                    : requestedFrom;
            dateTo = requestedTo == null ? dateFrom : requestedTo;
        }
        validateDateRange(dateFrom, dateTo);
        LocalDate materialisationDate = dateFrom.isBefore(today) ? today : dateFrom;
        while (!materialisationDate.isAfter(dateTo)) {
            materialiseActiveTemplates(principal.tenantId(), materialisationDate);
            materialisationDate = materialisationDate.plusDays(1);
        }

        Set<TaskStatus> statusFilter = statuses == null || statuses.isEmpty()
                ? status == null ? Set.of() : Set.of(status)
                : new LinkedHashSet<>(statuses);
        List<TaskRecord> records = statusFilter.isEmpty()
                ? recordRepository
                        .findAllByTenantIdAndTaskDateBetweenOrderByTaskDateDescTagNameAscTitleAsc(
                                principal.tenantId(), dateFrom, dateTo
                        )
                : recordRepository
                        .findAllByTenantIdAndTaskDateBetweenAndStatusInOrderByTaskDateDescTagNameAscTitleAsc(
                                principal.tenantId(), dateFrom, dateTo, statusFilter
                        );
        Set<UUID> assignedTagIds = principal.isOwner()
                ? Set.of()
                : assignedTagIds(principal);
        boolean oversight = accessPolicy.canOversee(principal.systemRole());

        List<TaskRecord> visible = new ArrayList<>(records.stream()
                .filter(record -> tagId == null || tagId.equals(record.getTagId()))
                .filter(record -> statusFilter.isEmpty() || statusFilter.contains(record.getStatus()))
                .filter(record -> !submittedByMe
                        || principal.userId().equals(record.getSubmittedByUserId()))
                .filter(record -> oversight
                        || principal.isOwner()
                        || principal.userId().equals(record.getSubmittedByUserId())
                        || isActiveTaskDate(record.getTaskDate(), today)
                            && assignedTagIds.contains(record.getTagId()))
                .toList());
        if (!dateFrom.isBefore(today)) {
            visible.sort(Comparator.comparing(TaskRecord::getTaskDate)
                    .thenComparing(TaskRecord::getTagName)
                    .thenComparing(TaskRecord::getTitle));
        }

        return new TaskListResponse(
                dateTo,
                dateFrom,
                dateTo,
                overviewOf(visible),
                toRecordResponses(principal, visible, assignedTagIds)
        );
    }

    @Transactional
    public TaskListResponse upcomingRecords(
            AuthenticatedUser principal,
            UUID tagId,
            int requestedLimit
    ) {
        requireTaskView(principal);
        if (requestedLimit < 1 || requestedLimit > 20) {
            throw badRequest(
                    "TASK_LIMIT_INVALID",
                    "Upcoming Task limit must be between 1 and 20."
            );
        }

        LocalDate today = today();
        LocalDate lastActiveDate = today.plusDays(ACTIVE_TASK_FUTURE_DAYS);
        Set<UUID> assignedTagIds = principal.isOwner()
                ? Set.of()
                : assignedTagIds(principal);
        boolean oversight = accessPolicy.canOversee(principal.systemRole());
        List<TaskRecord> visible = new ArrayList<>(requestedLimit);

        LocalDate taskDate = today;
        while (!taskDate.isAfter(lastActiveDate) && visible.size() < requestedLimit) {
            materialiseActiveTemplates(principal.tenantId(), taskDate);
            List<TaskRecord> records = recordRepository
                    .findAllByTenantIdAndTaskDateAndStatusOrderByTagNameAscTitleAsc(
                            principal.tenantId(), taskDate, TaskStatus.PENDING
                    );
            for (TaskRecord record : records) {
                if (record.getStatus() != TaskStatus.PENDING
                        || tagId != null && !tagId.equals(record.getTagId())
                        || !oversight && !principal.isOwner()
                            && !assignedTagIds.contains(record.getTagId())) {
                    continue;
                }
                visible.add(record);
                if (visible.size() == requestedLimit) break;
            }
            taskDate = taskDate.plusDays(1);
        }

        return new TaskListResponse(
                today,
                today,
                lastActiveDate,
                overviewOf(visible),
                toRecordResponses(principal, visible, assignedTagIds)
        );
    }

    public TaskListResponse approvals(AuthenticatedUser principal) {
        requireTaskRating(principal);
        Set<SystemRole> submitterRoles = rateableSubmitterRoles(principal);
        List<TaskRecord> records = submitterRoles.isEmpty()
                ? List.of()
                : recordRepository
                        .findAllByTenantIdAndStatusAndSubmittedByRoleInOrderBySubmittedAtAsc(
                                principal.tenantId(),
                                TaskStatus.SUBMITTED,
                                submitterRoles
                        );
        LocalDate today = today();
        LocalDate dateFrom = records.stream()
                .map(TaskRecord::getTaskDate)
                .min(LocalDate::compareTo)
                .orElse(today);
        LocalDate dateTo = records.stream()
                .map(TaskRecord::getTaskDate)
                .max(LocalDate::compareTo)
                .orElse(today);
        Set<UUID> assignedTagIds = Set.of();
        return new TaskListResponse(
                today,
                dateFrom,
                dateTo,
                overviewOf(records),
                toRecordResponses(principal, records, assignedTagIds)
        );
    }

    public int pendingApprovalCount(AuthenticatedUser principal) {
        requireTaskRating(principal);
        Set<SystemRole> submitterRoles = rateableSubmitterRoles(principal);
        if (submitterRoles.isEmpty()) return 0;
        return Math.toIntExact(
                recordRepository.countByTenantIdAndStatusAndSubmittedByRoleIn(
                        principal.tenantId(),
                        TaskStatus.SUBMITTED,
                        submitterRoles
                )
        );
    }

    @Transactional
    public TaskOverviewResponse overview(AuthenticatedUser principal, LocalDate requestedDate) {
        requireTaskView(principal);
        LocalDate today = today();
        LocalDate date = requestedDate == null ? today : requestedDate;
        validateDate(date);
        if (isActiveTaskDate(date, today)) {
            materialiseActiveTemplates(principal.tenantId(), date);
        }
        List<TaskRecord> records = recordRepository
                .findAllByTenantIdAndTaskDateOrderByTagNameAscTitleAsc(principal.tenantId(), date);

        if (!accessPolicy.canOversee(principal.systemRole()) && !principal.isOwner()) {
            Set<UUID> tags = assignedTagIds(principal);
            records = records.stream()
                    .filter(record -> tags.contains(record.getTagId()))
                    .toList();
        }
        return overviewOf(records);
    }

    public TaskRecordResponse record(AuthenticatedUser principal, UUID recordId) {
        requireTaskView(principal);
        TaskRecord record = requireRecord(principal.tenantId(), recordId);
        requireCanView(principal, record);
        return toRecordResponse(principal, record);
    }

    @Transactional
    public TaskRecordResponse submit(
            AuthenticatedUser principal,
            UUID recordId,
            String completedChecklistItemIds,
            List<MultipartFile> photos
    ) {
        requireTaskContribution(principal);
        TaskRecord record = requireRecordForUpdate(principal.tenantId(), recordId);
        if (record.getStatus() != TaskStatus.PENDING) {
            requireCanView(principal, record);
            String submitter = record.getSubmittedByUserId() == null
                    ? "another user"
                    : requireUser(principal.tenantId(), record.getSubmittedByUserId()).getFullName();
            throw conflict(
                    "TASK_ALREADY_SUBMITTED",
                    "This task has already been submitted by " + submitter + "."
            );
        }
        requireCanContribute(principal, record);
        List<TaskRecordChecklistItem> checks = recordChecklistRepository
                .findAllByTenantIdAndRecordIdOrderByPositionAsc(principal.tenantId(), recordId);
        Set<UUID> completedIds = parseChecklistItemIds(completedChecklistItemIds);
        Set<UUID> requiredIds = checks.stream()
                .map(TaskRecordChecklistItem::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (checks.isEmpty() || !completedIds.equals(requiredIds)) {
            throw badRequest(
                    "TASK_CHECKLIST_INCOMPLETE",
                    "Complete every checklist item before submitting."
            );
        }
        if (photos.size() < record.getRequiredPhotoCount()) {
            throw badRequest(
                    "TASK_PHOTOS_INCOMPLETE",
                    "Take at least " + record.getRequiredPhotoCount() + " confirmed photos before submitting."
            );
        }
        if (photos.size() > MAX_CONFIRMED_PHOTOS_PER_TASK) {
            throw badRequest(
                    "TASK_PHOTO_LIMIT",
                    "A Task may contain at most " + MAX_CONFIRMED_PHOTOS_PER_TASK + " photos."
            );
        }

        Instant submittedAt = Instant.now();
        checks.forEach(item -> item.setCompleted(true, principal.userId(), submittedAt));
        recordChecklistRepository.saveAll(checks);

        List<TaskPhoto> selectedPhotos = new ArrayList<>();
        for (MultipartFile file : photos) {
            ReportMedia media = mediaService.saveImageEntity(principal, file);
            selectedPhotos.add(new TaskPhoto(
                    principal.tenantId(), recordId, media.getId(), principal.userId()
            ));
        }
        photoRepository.saveAllAndFlush(selectedPhotos);

        record.submit(principal.userId(), principal.systemRole(), submittedAt);
        auditRepository.save(new TaskAuditEntry(
                principal.tenantId(), record.getTemplateId(), recordId, principal.userId(),
                "TASK_SUBMITTED",
                "Final evidence stored: " + checks.size() + " checklist item(s), "
                        + selectedPhotos.size() + " photo(s); task submitted for rating"
        ));
        return toRecordResponse(principal, record);
    }

    @Transactional
    public TaskRecordResponse rate(
            AuthenticatedUser principal,
            UUID recordId,
            RateTaskRequest request
    ) {
        requireTaskRating(principal);
        TaskRecord record = requireRecordForUpdate(principal.tenantId(), recordId);
        if (record.getStatus() != TaskStatus.SUBMITTED) {
            throw conflict("TASK_NOT_SUBMITTED", "Only a submitted task may be rated.");
        }
        if (!accessPolicy.canRate(principal.systemRole(), record.getSubmittedByRole())) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "TASK_RATING_RANK_REQUIRED",
                    "Only a higher-ranked Manager, Head or Owner may rate this submission."
            );
        }
        record.rate(request.rating(), request.comment(), principal.userId(), Instant.now());
        auditRepository.save(new TaskAuditEntry(
                principal.tenantId(), record.getTemplateId(), recordId, principal.userId(),
                "TASK_RATED", request.rating() + " star(s): " + request.comment().trim()
        ));
        return toRecordResponse(principal, record);
    }

    private void materialiseActiveTemplates(UUID tenantId, LocalDate date) {
        List<TaskTemplate> templates = templateRepository
                .findAllByTenantIdAndActiveTrueOrderByTitleAsc(tenantId)
                .stream()
                .filter(template -> template.isScheduledFor(date))
                .toList();
        if (templates.isEmpty()) return;
        Set<UUID> existingBeforeLock = recordRepository
                .findAllByTenantIdAndTaskDateOrderByTagNameAscTitleAsc(tenantId, date)
                .stream()
                .map(TaskRecord::getTemplateId)
                .collect(Collectors.toSet());
        if (templates.stream().allMatch(template -> existingBeforeLock.contains(template.getId()))) return;

        lockTenant(tenantId);
        Set<UUID> existingAfterLock = recordRepository
                .findAllByTenantIdAndTaskDateOrderByTagNameAscTitleAsc(tenantId, date)
                .stream()
                .map(TaskRecord::getTemplateId)
                .collect(Collectors.toSet());
        List<TaskTemplate> missing = templates.stream()
                .filter(template -> !existingAfterLock.contains(template.getId()))
                .toList();
        if (missing.isEmpty()) return;
        Map<UUID, StockTag> tags = tagRepository.findAllByTenant_IdAndIdIn(
                        tenantId,
                        missing.stream().map(TaskTemplate::getTagId).collect(Collectors.toSet())
                ).stream()
                .collect(Collectors.toMap(StockTag::getId, tag -> tag));
        Map<UUID, List<TaskTemplateChecklistItem>> checks = templateChecklistRepository
                .findAllByTenantIdAndTemplateIdIn(
                        tenantId,
                        missing.stream().map(TaskTemplate::getId).toList()
                ).stream()
                .sorted(Comparator.comparingInt(TaskTemplateChecklistItem::getPosition))
                .collect(Collectors.groupingBy(TaskTemplateChecklistItem::getTemplateId));
        for (TaskTemplate template : missing) {
            StockTag tag = tags.get(template.getTagId());
            if (tag == null) {
                throw notFound("STOCK_TAG_NOT_FOUND", "Stock tag was not found.");
            }
            createMaterialisedTemplate(
                    template,
                    date,
                    tag,
                    checks.getOrDefault(template.getId(), List.of())
            );
        }
    }

    private TaskRecord materialiseTemplate(
            TaskTemplate template,
            LocalDate date,
            StockTag tag
    ) {
        return recordRepository.findByTenantIdAndTemplateIdAndTaskDate(
                        template.getTenantId(), template.getId(), date
                )
                .orElseGet(() -> createMaterialisedTemplate(template, date, tag));
    }

    private TaskRecord createMaterialisedTemplate(
            TaskTemplate template,
            LocalDate date,
            StockTag tag
    ) {
        List<TaskTemplateChecklistItem> templateChecks = templateChecklistRepository
                .findAllByTenantIdAndTemplateIdOrderByPositionAsc(
                        template.getTenantId(), template.getId()
                );
        return createMaterialisedTemplate(template, date, tag, templateChecks);
    }

    private TaskRecord createMaterialisedTemplate(
            TaskTemplate template,
            LocalDate date,
            StockTag tag,
            List<TaskTemplateChecklistItem> templateChecks
    ) {
        TaskRecord saved = recordRepository.saveAndFlush(
                new TaskRecord(template, date, tag.getTag())
        );
        recordChecklistRepository.saveAll(templateChecks.stream()
                .map(item -> new TaskRecordChecklistItem(
                        template.getTenantId(),
                        saved.getId(),
                        item.getPosition(),
                        item.getDescription()
                ))
                .toList());
        return saved;
    }

    private void lockTenant(UUID tenantId) {
        tenantRepository.findLockedById(tenantId)
                .orElseThrow(() -> notFound("TENANT_NOT_FOUND", "Business was not found."));
    }

    private void saveTemplateChecklist(UUID tenantId, UUID templateId, List<String> items) {
        if (items == null || items.isEmpty() || items.size() > 5) {
            throw badRequest(
                    "TASK_CHECKLIST_SIZE",
                    "A task requires between 1 and 5 checklist items."
            );
        }
        List<TaskTemplateChecklistItem> entities = new ArrayList<>();
        for (int index = 0; index < items.size(); index++) {
            String description = items.get(index) == null ? "" : items.get(index).trim();
            if (description.isEmpty()) {
                throw badRequest(
                        "TASK_CHECKLIST_DESCRIPTION_REQUIRED",
                        "Every checklist item needs a description."
                );
            }
            entities.add(new TaskTemplateChecklistItem(
                    tenantId, templateId, index, description
            ));
        }
        templateChecklistRepository.saveAll(entities);
    }

    private TaskTemplateResponse toTemplateResponse(
            UUID tenantId,
            TaskTemplate template,
            String linkedSopTitle
    ) {
        StockTag tag = requireTag(tenantId, template.getTagId());
        List<String> checks = templateChecklistRepository
                .findAllByTenantIdAndTemplateIdOrderByPositionAsc(tenantId, template.getId())
                .stream()
                .map(TaskTemplateChecklistItem::getDescription)
                .toList();
        return new TaskTemplateResponse(
                template.getId(),
                template.getTagId(),
                tag.getTag(),
                template.getTitle(),
                template.getInstruction(),
                template.getLinkedSopId(),
                linkedSopTitle,
                template.getRequiredPhotoCount(),
                template.getScheduleType(),
                template.getFirstTaskDate(),
                template.getEndDate(),
                checks,
                template.isActive(),
                person(tenantId, template.getCreatedByUserId()),
                person(tenantId, template.getUpdatedByUserId()),
                template.getCreatedAt(),
                template.getUpdatedAt(),
                List.of()
        );
    }

    private TaskRecordResponse toRecordResponse(
            AuthenticatedUser principal,
            TaskRecord record
    ) {
        Set<UUID> assignedTagIds = principal.isOwner()
                ? Set.of()
                : assignedTagIds(principal);
        return toRecordResponses(principal, List.of(record), assignedTagIds).getFirst();
    }

    private List<TaskRecordResponse> toRecordResponses(
            AuthenticatedUser principal,
            List<TaskRecord> records,
            Set<UUID> assignedTagIds
    ) {
        if (records.isEmpty()) return List.of();
        UUID tenantId = principal.tenantId();
        Set<UUID> recordIds = records.stream()
                .map(TaskRecord::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<UUID, List<TaskRecordChecklistItem>> checksByRecord = new HashMap<>();
        for (TaskRecordChecklistItem item : recordChecklistRepository
                .findAllByTenantIdAndRecordIdIn(tenantId, recordIds)) {
            checksByRecord.computeIfAbsent(item.getRecordId(), ignored -> new ArrayList<>())
                    .add(item);
        }
        checksByRecord.values().forEach(items -> items.sort(
                Comparator.comparingInt(TaskRecordChecklistItem::getPosition)
                        .thenComparing(TaskRecordChecklistItem::getId)
        ));

        Map<UUID, List<TaskPhoto>> photosByRecord = new HashMap<>();
        for (TaskPhoto photo : photoRepository
                .findAllByTenantIdAndRecordIdIn(tenantId, recordIds)) {
            photosByRecord.computeIfAbsent(photo.getRecordId(), ignored -> new ArrayList<>())
                    .add(photo);
        }
        photosByRecord.values().forEach(items -> items.sort(
                Comparator.comparing(
                                TaskPhoto::getSubmittedAt,
                                Comparator.nullsLast(Comparator.naturalOrder())
                        )
                        .thenComparing(TaskPhoto::getId)
        ));

        Map<UUID, List<TaskAuditEntry>> activityByRecord = new HashMap<>();
        for (TaskAuditEntry entry : auditRepository
                .findAllByTenantIdAndRecordIdIn(tenantId, recordIds)) {
            activityByRecord.computeIfAbsent(entry.getRecordId(), ignored -> new ArrayList<>())
                    .add(entry);
        }
        activityByRecord.values().forEach(items -> items.sort(
                Comparator.comparing(
                                TaskAuditEntry::getOccurredAt,
                                Comparator.nullsLast(Comparator.naturalOrder())
                        )
                        .thenComparing(TaskAuditEntry::getId)
        ));

        List<UUID> mediaIds = photosByRecord.values().stream()
                .flatMap(List::stream)
                .map(TaskPhoto::getPhotoMediaId)
                .distinct()
                .toList();
        Map<UUID, String> storageKeyByMediaId = mediaIds.isEmpty()
                ? Map.of()
                : mediaRepository.findAllReferencesByTenantIdAndIdIn(tenantId, mediaIds)
                        .stream()
                        .collect(Collectors.toMap(
                                ReportMediaReference::id,
                                ReportMediaReference::storageKey
                        ));

        Set<UUID> userIds = new LinkedHashSet<>();
        for (TaskRecord record : records) {
            if (record.getSubmittedByUserId() != null) userIds.add(record.getSubmittedByUserId());
            if (record.getRatedByUserId() != null) userIds.add(record.getRatedByUserId());
        }
        checksByRecord.values().stream()
                .flatMap(List::stream)
                .map(TaskRecordChecklistItem::getCompletedByUserId)
                .filter(java.util.Objects::nonNull)
                .forEach(userIds::add);
        photosByRecord.values().stream()
                .flatMap(List::stream)
                .map(TaskPhoto::getSubmittedByUserId)
                .forEach(userIds::add);
        activityByRecord.values().stream()
                .flatMap(List::stream)
                .map(TaskAuditEntry::getActorUserId)
                .forEach(userIds::add);
        Map<UUID, UserAccount> usersById = userIds.isEmpty()
                ? Map.of()
                : userRepository.findAllByTenant_IdAndIdIn(tenantId, userIds)
                        .stream()
                        .collect(Collectors.toMap(UserAccount::getId, user -> user));

        Map<UUID, String> linkedSopTitles = linkedSopTitles(
                tenantId,
                records.stream()
                        .map(TaskRecord::getLinkedSopId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet())
        );

        List<TaskRecordResponse> responses = new ArrayList<>(records.size());
        for (TaskRecord record : records) {
            List<TaskRecordChecklistItem> checks = checksByRecord
                    .getOrDefault(record.getId(), List.of());
            List<TaskPhoto> photos = photosByRecord
                    .getOrDefault(record.getId(), List.of());
            responses.add(toRecordResponse(
                    principal,
                    record,
                    checks,
                    photos,
                    activityByRecord.getOrDefault(record.getId(), List.of()),
                    storageKeyByMediaId,
                    usersById,
                    assignedTagIds,
                    linkedSopTitle(linkedSopTitles, record.getLinkedSopId())
            ));
        }
        return List.copyOf(responses);
    }

    private TaskRecordResponse toRecordResponse(
            AuthenticatedUser principal,
            TaskRecord record,
            List<TaskRecordChecklistItem> checks,
            List<TaskPhoto> photos,
            List<TaskAuditEntry> activity,
            Map<UUID, String> storageKeyByMediaId,
            Map<UUID, UserAccount> usersById,
            Set<UUID> assignedTagIds,
            String linkedSopTitle
    ) {
        boolean requirementsMet = photos.size() >= record.getRequiredPhotoCount()
                && !checks.isEmpty()
                && checks.stream().allMatch(TaskRecordChecklistItem::isCompleted);
        boolean canContribute = record.getStatus() == TaskStatus.PENDING
                && record.getTaskDate().equals(today())
                && (principal.isOwner() || assignedTagIds.contains(record.getTagId()));
        boolean canRate = record.getStatus() == TaskStatus.SUBMITTED
                && accessPolicy.canRate(principal.systemRole(), record.getSubmittedByRole());

        List<TaskChecklistItemResponse> checkResponses = checks.stream()
                .map(item -> new TaskChecklistItemResponse(
                        item.getId(),
                        item.getPosition(),
                        item.getDescription(),
                        item.isCompleted(),
                        personOrNull(usersById, item.getCompletedByUserId()),
                        item.getCompletedAt()
                ))
                .toList();
        List<TaskPhotoResponse> photoResponses = photos.stream()
                .map(photo -> {
                    String storageKey = storageKeyByMediaId.get(photo.getPhotoMediaId());
                    if (storageKey == null) {
                        throw notFound(
                                    "TASK_PHOTO_MEDIA_NOT_FOUND",
                                    "A confirmed task photo was not found."
                        );
                    }
                    return new TaskPhotoResponse(
                            photo.getId(),
                            storageKey,
                            person(usersById, photo.getSubmittedByUserId()),
                            photo.getSubmittedAt()
                    );
                })
                .toList();
        List<TaskAuditResponse> activityResponses = activity.stream()
                .map(entry -> toAuditResponse(usersById, entry))
                .toList();
        return new TaskRecordResponse(
                record.getId(),
                record.getTemplateId(),
                record.getTagId(),
                record.getTagName(),
                record.getTaskDate(),
                record.getTitle(),
                record.getInstruction(),
                record.getLinkedSopId(),
                linkedSopTitle,
                record.getRequiredPhotoCount(),
                record.getScheduleType(),
                photoResponses.size(),
                record.getStatus(),
                checkResponses,
                photoResponses,
                requirementsMet,
                personOrNull(usersById, record.getSubmittedByUserId()),
                record.getSubmittedAt(),
                record.getRating(),
                record.getRatingComment(),
                personOrNull(usersById, record.getRatedByUserId()),
                record.getRatedAt(),
                canContribute,
                canContribute && requirementsMet,
                canRate,
                activityResponses
        );
    }

    private TaskOverviewResponse overviewOf(List<TaskRecord> records) {
        int pending = 0;
        int submitted = 0;
        int done = 0;
        for (TaskRecord record : records) {
            switch (record.getStatus()) {
                case PENDING -> pending++;
                case SUBMITTED -> submitted++;
                case DONE -> done++;
            }
        }
        return new TaskOverviewResponse(records.size(), pending, submitted, done);
    }

    private Set<UUID> assignedTagIds(AuthenticatedUser principal) {
        return tagAssigneeRepository
                .findAllByTenantIdAndUserId(principal.tenantId(), principal.userId())
                .stream()
                .map(assignment -> assignment.getTagId())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<SystemRole> rateableSubmitterRoles(AuthenticatedUser principal) {
        Set<SystemRole> roles = new LinkedHashSet<>();
        for (SystemRole role : SystemRole.values()) {
            if (accessPolicy.canRate(principal.systemRole(), role)) {
                roles.add(role);
            }
        }
        return roles;
    }

    private boolean canContribute(AuthenticatedUser principal, TaskRecord record) {
        return principal.isOwner() || tagAssigneeRepository.existsByTenantIdAndTagIdAndUserId(
                principal.tenantId(), record.getTagId(), principal.userId()
        );
    }

    private void requireCanContribute(AuthenticatedUser principal, TaskRecord record) {
        if (!record.getTaskDate().equals(today())) {
            throw conflict(
                    "TASK_NOT_DUE_TODAY",
                    "This Task can only be completed on its scheduled date."
            );
        }
        if (!canContribute(principal, record)) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "TASK_TAG_ACCESS_REQUIRED",
                    "Only users assigned to this task's tag may contribute or submit."
            );
        }
    }

    private void requireCanView(AuthenticatedUser principal, TaskRecord record) {
        LocalDate today = today();
        if (accessPolicy.canOversee(principal.systemRole())
                || principal.isOwner()
                || principal.userId().equals(record.getSubmittedByUserId())
                || isActiveTaskDate(record.getTaskDate(), today)
                    && canContribute(principal, record)) {
            return;
        }
        throw new ApiException(
                HttpStatus.FORBIDDEN,
                "TASK_VIEW_DENIED",
                "This task is not available to the current user."
        );
    }

    private Set<UUID> parseChecklistItemIds(String value) {
        Set<UUID> ids = new LinkedHashSet<>();
        if (value == null || value.isBlank()) return ids;
        for (String rawId : value.split(",")) {
            String itemId = rawId.trim();
            if (itemId.isEmpty()) {
                throw badRequest(
                        "TASK_CHECKLIST_INVALID",
                        "The submitted checklist is invalid."
                );
            }
            UUID id;
            try {
                id = UUID.fromString(itemId);
            } catch (IllegalArgumentException exception) {
                throw badRequest(
                        "TASK_CHECKLIST_INVALID",
                        "The submitted checklist is invalid."
                );
            }
            if (!ids.add(id)) {
                throw badRequest(
                        "TASK_CHECKLIST_INVALID",
                        "The submitted checklist contains a duplicate item."
                );
            }
        }
        return ids;
    }

    private TaskTemplate requireTemplate(UUID tenantId, UUID templateId) {
        return templateRepository.findByIdAndTenantId(templateId, tenantId)
                .orElseThrow(() -> notFound("TASK_TEMPLATE_NOT_FOUND", "Task template was not found."));
    }

    private TaskRecord requireRecord(UUID tenantId, UUID recordId) {
        return recordRepository.findByIdAndTenantId(recordId, tenantId)
                .orElseThrow(() -> notFound("TASK_NOT_FOUND", "Task was not found."));
    }

    private TaskRecord requireRecordForUpdate(UUID tenantId, UUID recordId) {
        return recordRepository.findLockedByIdAndTenantId(recordId, tenantId)
                .orElseThrow(() -> notFound("TASK_NOT_FOUND", "Task was not found."));
    }

    private StockTag requireTag(UUID tenantId, UUID tagId) {
        return tagRepository.findByIdAndTenant_Id(tagId, tenantId)
                .orElseThrow(() -> notFound("STOCK_TAG_NOT_FOUND", "The selected tag was not found."));
    }

    private KnowledgeSop requireLinkedSopOrNull(UUID tenantId, UUID linkedSopId) {
        if (linkedSopId == null) return null;
        return knowledgeSopRepository.findByIdAndTenant_Id(linkedSopId, tenantId)
                .orElseThrow(() -> notFound(
                        "TASK_LINKED_SOP_NOT_FOUND",
                        "The selected SOP video was not found in the active business."
                ));
    }

    private Map<UUID, String> linkedSopTitles(UUID tenantId, Set<UUID> linkedSopIds) {
        if (linkedSopIds.isEmpty()) return Map.of();
        return knowledgeSopRepository.findAllByTenant_IdAndIdIn(tenantId, linkedSopIds)
                .stream()
                .collect(Collectors.toMap(KnowledgeSop::getId, KnowledgeSop::getTitle));
    }

    static String linkedSopTitle(Map<UUID, String> titlesById, UUID linkedSopId) {
        return linkedSopId == null ? null : titlesById.get(linkedSopId);
    }

    private UserAccount requireUser(UUID tenantId, UUID userId) {
        return userRepository.findByIdAndTenant_Id(userId, tenantId)
                .orElseThrow(() -> notFound("USER_NOT_FOUND", "User was not found."));
    }

    private TaskPersonResponse person(UUID tenantId, UUID userId) {
        UserAccount user = requireUser(tenantId, userId);
        return person(user);
    }

    private TaskPersonResponse person(Map<UUID, UserAccount> usersById, UUID userId) {
        UserAccount user = usersById.get(userId);
        if (user == null) {
            throw notFound("USER_NOT_FOUND", "User was not found.");
        }
        return person(user);
    }

    private TaskPersonResponse person(UserAccount user) {
        return new TaskPersonResponse(
                user.getId(),
                user.getFullName(),
                user.getEmployeeId(),
                user.getRole().getSystemKey()
        );
    }

    private TaskAuditResponse toAuditResponse(
            UUID tenantId,
            TaskAuditEntry entry
    ) {
        return new TaskAuditResponse(
                entry.getId(),
                entry.getAction(),
                entry.getDetails(),
                person(tenantId, entry.getActorUserId()),
                entry.getOccurredAt()
        );
    }

    private TaskAuditResponse toAuditResponse(
            Map<UUID, UserAccount> usersById,
            TaskAuditEntry entry
    ) {
        return new TaskAuditResponse(
                entry.getId(),
                entry.getAction(),
                entry.getDetails(),
                person(usersById, entry.getActorUserId()),
                entry.getOccurredAt()
        );
    }

    private TaskPersonResponse personOrNull(UUID tenantId, UUID userId) {
        return userId == null ? null : person(tenantId, userId);
    }

    private TaskPersonResponse personOrNull(
            Map<UUID, UserAccount> usersById,
            UUID userId
    ) {
        return userId == null ? null : person(usersById, userId);
    }

    private void requireManagement(AuthenticatedUser principal) {
        if (!principal.hasPermission(SystemPermission.TASK_MANAGE)) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "TASK_MANAGEMENT_REQUIRED",
                    "Only Owner, Head or Manager may manage task templates."
            );
        }
    }

    private void validateTemplateSchedule(UpsertTaskTemplateRequest request) {
        if (request.endDate() != null && request.endDate().isBefore(request.firstTaskDate())) {
            throw badRequest(
                    "TASK_END_DATE_INVALID",
                    "End date must not be before the first task date."
            );
        }
        if (request.scheduleType() == TaskScheduleType.AD_HOC
                && request.endDate() != null) {
            throw badRequest(
                    "TASK_END_DATE_NOT_ALLOWED",
                    "An ad hoc task does not need an end date."
            );
        }
    }

    private void requireTaskView(AuthenticatedUser principal) {
        requirePermission(
                principal,
                SystemPermission.TASK_VIEW,
                "TASK_ACCESS_REQUIRED",
                "Task access has not been granted to this role."
        );
    }

    private void requireTaskContribution(AuthenticatedUser principal) {
        requirePermission(
                principal,
                SystemPermission.TASK_CONTRIBUTE,
                "TASK_CONTRIBUTION_REQUIRED",
                "Task submission has not been granted to this role."
        );
    }

    private void requireTaskRating(AuthenticatedUser principal) {
        requirePermission(
                principal,
                SystemPermission.TASK_RATE,
                "TASK_RATING_REQUIRED",
                "Task rating has not been granted to this role."
        );
    }

    private void requirePermission(
            AuthenticatedUser principal,
            SystemPermission permission,
            String code,
            String message
    ) {
        if (!principal.hasPermission(permission)) {
            throw new ApiException(HttpStatus.FORBIDDEN, code, message);
        }
    }

    private void validateDate(LocalDate date) {
        if (date.isAfter(today().plusDays(ACTIVE_TASK_FUTURE_DAYS))) {
            throw badRequest(
                    "TASK_FUTURE_DATE",
                    "Tasks may be loaded up to 10 days ahead."
            );
        }
    }

    private boolean isActiveTaskDate(LocalDate date, LocalDate today) {
        return !date.isBefore(today)
                && !date.isAfter(today.plusDays(ACTIVE_TASK_FUTURE_DAYS));
    }

    private void validateDateRange(LocalDate dateFrom, LocalDate dateTo) {
        if (dateFrom.isAfter(dateTo)) {
            throw badRequest(
                    "TASK_DATE_RANGE_INVALID",
                    "Task start date must not be after the end date."
            );
        }
        validateDate(dateFrom);
        validateDate(dateTo);
        if (dateFrom.plusDays(MAX_HISTORY_RANGE_DAYS - 1L).isBefore(dateTo)) {
            throw badRequest(
                    "TASK_DATE_RANGE_TOO_LARGE",
                    "Task history may be loaded for a maximum of 30 days."
            );
        }
    }

    private LocalDate today() {
        return LocalDate.now(reportProperties.zoneId());
    }

    private static ApiException notFound(String code, String message) {
        return new ApiException(HttpStatus.NOT_FOUND, code, message);
    }

    private static ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    private static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }
}
