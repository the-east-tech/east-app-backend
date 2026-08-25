package com.eastapp.backend.tasks.service;

import com.eastapp.backend.auth.security.AuthenticatedUser;
import com.eastapp.backend.common.error.ApiException;
import com.eastapp.backend.organisation.TenantRepository;
import com.eastapp.backend.people.SystemRole;
import com.eastapp.backend.people.UserAccount;
import com.eastapp.backend.people.UserAccountRepository;
import com.eastapp.backend.reports.ReportMedia;
import com.eastapp.backend.reports.ReportMediaRepository;
import com.eastapp.backend.reports.config.ReportProperties;
import com.eastapp.backend.reports.service.ReportMediaService;
import com.eastapp.backend.stock.StockTag;
import com.eastapp.backend.stock.StockTagAssigneeRepository;
import com.eastapp.backend.stock.StockTagRepository;
import com.eastapp.backend.tasks.DailyTaskAuditEntry;
import com.eastapp.backend.tasks.DailyTaskAuditEntryRepository;
import com.eastapp.backend.tasks.DailyTaskPhoto;
import com.eastapp.backend.tasks.DailyTaskPhotoRepository;
import com.eastapp.backend.tasks.DailyTaskRecord;
import com.eastapp.backend.tasks.DailyTaskRecordChecklistItem;
import com.eastapp.backend.tasks.DailyTaskRecordChecklistItemRepository;
import com.eastapp.backend.tasks.DailyTaskRecordRepository;
import com.eastapp.backend.tasks.DailyTaskStatus;
import com.eastapp.backend.tasks.DailyTaskTemplate;
import com.eastapp.backend.tasks.DailyTaskTemplateChecklistItem;
import com.eastapp.backend.tasks.DailyTaskTemplateChecklistItemRepository;
import com.eastapp.backend.tasks.DailyTaskTemplateRepository;
import com.eastapp.backend.tasks.api.DailyTaskAuditResponse;
import com.eastapp.backend.tasks.api.DailyTaskChecklistItemResponse;
import com.eastapp.backend.tasks.api.DailyTaskListResponse;
import com.eastapp.backend.tasks.api.DailyTaskOverviewResponse;
import com.eastapp.backend.tasks.api.DailyTaskPersonResponse;
import com.eastapp.backend.tasks.api.DailyTaskPhotoResponse;
import com.eastapp.backend.tasks.api.DailyTaskRecordResponse;
import com.eastapp.backend.tasks.api.DailyTaskTemplateResponse;
import com.eastapp.backend.tasks.api.RateDailyTaskRequest;
import com.eastapp.backend.tasks.api.UpsertDailyTaskTemplateRequest;
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
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class DailyTaskService {
    private static final int MAX_CONFIRMED_PHOTOS_PER_TASK = 40;
    private static final int MAX_HISTORY_RANGE_DAYS = 30;

    private final DailyTaskTemplateRepository templateRepository;
    private final DailyTaskTemplateChecklistItemRepository templateChecklistRepository;
    private final DailyTaskRecordRepository recordRepository;
    private final DailyTaskRecordChecklistItemRepository recordChecklistRepository;
    private final DailyTaskPhotoRepository photoRepository;
    private final DailyTaskAuditEntryRepository auditRepository;
    private final StockTagRepository tagRepository;
    private final StockTagAssigneeRepository tagAssigneeRepository;
    private final UserAccountRepository userRepository;
    private final TenantRepository tenantRepository;
    private final ReportMediaRepository mediaRepository;
    private final ReportMediaService mediaService;
    private final ReportProperties reportProperties;
    private final DailyTaskAccessPolicy accessPolicy;

    public DailyTaskService(
            DailyTaskTemplateRepository templateRepository,
            DailyTaskTemplateChecklistItemRepository templateChecklistRepository,
            DailyTaskRecordRepository recordRepository,
            DailyTaskRecordChecklistItemRepository recordChecklistRepository,
            DailyTaskPhotoRepository photoRepository,
            DailyTaskAuditEntryRepository auditRepository,
            StockTagRepository tagRepository,
            StockTagAssigneeRepository tagAssigneeRepository,
            UserAccountRepository userRepository,
            TenantRepository tenantRepository,
            ReportMediaRepository mediaRepository,
            ReportMediaService mediaService,
            ReportProperties reportProperties,
            DailyTaskAccessPolicy accessPolicy
    ) {
        this.templateRepository = templateRepository;
        this.templateChecklistRepository = templateChecklistRepository;
        this.recordRepository = recordRepository;
        this.recordChecklistRepository = recordChecklistRepository;
        this.photoRepository = photoRepository;
        this.auditRepository = auditRepository;
        this.tagRepository = tagRepository;
        this.tagAssigneeRepository = tagAssigneeRepository;
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.mediaRepository = mediaRepository;
        this.mediaService = mediaService;
        this.reportProperties = reportProperties;
        this.accessPolicy = accessPolicy;
    }

    public List<DailyTaskTemplateResponse> templates(AuthenticatedUser principal) {
        requireManagement(principal);
        return templateRepository.findAllByTenantIdOrderByActiveDescTitleAsc(principal.tenantId())
                .stream()
                .map(template -> toTemplateResponse(principal.tenantId(), template))
                .toList();
    }

    @Transactional
    public DailyTaskTemplateResponse createTemplate(
            AuthenticatedUser principal,
            UpsertDailyTaskTemplateRequest request
    ) {
        requireManagement(principal);
        StockTag tag = requireTag(principal.tenantId(), request.tagId());
        UserAccount actor = requireUser(principal.tenantId(), principal.userId());
        DailyTaskTemplate template = templateRepository.saveAndFlush(new DailyTaskTemplate(
                principal.tenantId(),
                tag.getId(),
                request.title(),
                request.instruction(),
                request.requiredPhotoCount(),
                request.active(),
                actor.getId()
        ));
        saveTemplateChecklist(principal.tenantId(), template.getId(), request.checklistItems());
        auditRepository.save(new DailyTaskAuditEntry(
                principal.tenantId(), template.getId(), null, actor.getId(),
                "TEMPLATE_CREATED",
                "Tag: " + tag.getTag()
                        + "; title: " + template.getTitle()
                        + "; required photos: " + template.getRequiredPhotoCount()
                        + "; checklist items: " + request.checklistItems().size()
                        + "; active: " + template.isActive()
        ));
        if (template.isActive()) {
            lockTenant(principal.tenantId());
            materialiseTemplate(template, today(), tag);
        }
        return toTemplateResponse(principal.tenantId(), template);
    }

    @Transactional
    public DailyTaskTemplateResponse updateTemplate(
            AuthenticatedUser principal,
            UUID templateId,
            UpsertDailyTaskTemplateRequest request
    ) {
        requireManagement(principal);
        DailyTaskTemplate template = requireTemplate(principal.tenantId(), templateId);
        StockTag previousTag = requireTag(principal.tenantId(), template.getTagId());
        String previousTitle = template.getTitle();
        String previousInstruction = template.getInstruction();
        int previousPhotoCount = template.getRequiredPhotoCount();
        boolean previousActive = template.isActive();
        List<String> previousChecklist = templateChecklistRepository
                .findAllByTenantIdAndTemplateIdOrderByPositionAsc(
                        principal.tenantId(), templateId
                )
                .stream()
                .map(DailyTaskTemplateChecklistItem::getDescription)
                .toList();
        // Freeze today's version before changing the template. Edits affect the
        // next business date, while a newly-created template starts today.
        if (template.isActive()) {
            lockTenant(principal.tenantId());
            materialiseTemplate(template, today(), previousTag);
        }

        StockTag nextTag = requireTag(principal.tenantId(), request.tagId());
        template.update(
                nextTag.getId(),
                request.title(),
                request.instruction(),
                request.requiredPhotoCount(),
                request.active(),
                principal.userId()
        );
        templateChecklistRepository.deleteAllByTenantIdAndTemplateId(
                principal.tenantId(), templateId
        );
        templateChecklistRepository.flush();
        saveTemplateChecklist(principal.tenantId(), templateId, request.checklistItems());
        auditRepository.save(new DailyTaskAuditEntry(
                principal.tenantId(), templateId, null, principal.userId(),
                "TEMPLATE_UPDATED",
                "Tag: " + previousTag.getTag() + " -> " + nextTag.getTag()
                        + "; title: " + previousTitle + " -> " + template.getTitle()
                        + "; instruction changed: "
                        + !previousInstruction.equals(template.getInstruction())
                        + "; required photos: " + previousPhotoCount
                        + " -> " + template.getRequiredPhotoCount()
                        + "; checklist changed: "
                        + !previousChecklist.equals(request.checklistItems().stream()
                                .map(String::trim).toList())
                        + "; active: " + previousActive + " -> " + template.isActive()
        ));
        return toTemplateResponse(principal.tenantId(), template);
    }

    @Transactional
    public DailyTaskListResponse records(
            AuthenticatedUser principal,
            LocalDate requestedDate,
            LocalDate requestedFrom,
            LocalDate requestedTo,
            UUID tagId,
            DailyTaskStatus status,
            boolean submittedByMe
    ) {
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
        if (!dateFrom.isAfter(today) && !dateTo.isBefore(today)) {
            materialiseActiveTemplates(principal.tenantId(), today);
        }

        List<DailyTaskRecord> records = recordRepository
                .findAllByTenantIdAndTaskDateBetweenOrderByTaskDateDescTagNameAscTitleAsc(
                        principal.tenantId(), dateFrom, dateTo
                );
        Set<UUID> assignedTagIds = principal.isOwner()
                ? Set.of()
                : assignedTagIds(principal);
        boolean oversight = accessPolicy.canOversee(principal.systemRole());

        List<DailyTaskRecord> visible = records.stream()
                .filter(record -> tagId == null || tagId.equals(record.getTagId()))
                .filter(record -> status == null || status == record.getStatus())
                .filter(record -> !submittedByMe
                        || principal.userId().equals(record.getSubmittedByUserId()))
                .filter(record -> oversight
                        || principal.isOwner()
                        || principal.userId().equals(record.getSubmittedByUserId())
                        || record.getTaskDate().equals(today)
                            && assignedTagIds.contains(record.getTagId()))
                .toList();

        return new DailyTaskListResponse(
                dateTo,
                dateFrom,
                dateTo,
                overviewOf(visible),
                toRecordResponses(principal, visible, assignedTagIds)
        );
    }

    @Transactional
    public DailyTaskOverviewResponse overview(AuthenticatedUser principal, LocalDate requestedDate) {
        LocalDate date = requestedDate == null ? today() : requestedDate;
        validateDate(date);
        if (date.equals(today())) materialiseActiveTemplates(principal.tenantId(), date);
        List<DailyTaskRecord> records = recordRepository
                .findAllByTenantIdAndTaskDateOrderByTagNameAscTitleAsc(principal.tenantId(), date);

        if (!accessPolicy.canOversee(principal.systemRole()) && !principal.isOwner()) {
            Set<UUID> tags = assignedTagIds(principal);
            records = records.stream()
                    .filter(record -> tags.contains(record.getTagId()))
                    .toList();
        }
        return overviewOf(records);
    }

    public DailyTaskRecordResponse record(AuthenticatedUser principal, UUID recordId) {
        DailyTaskRecord record = requireRecord(principal.tenantId(), recordId);
        requireCanView(principal, record);
        return toRecordResponse(principal, record);
    }

    @Transactional
    public DailyTaskRecordResponse submit(
            AuthenticatedUser principal,
            UUID recordId,
            String completedChecklistItemIds,
            List<MultipartFile> photos
    ) {
        DailyTaskRecord record = requireRecordForUpdate(principal.tenantId(), recordId);
        if (record.getStatus() != DailyTaskStatus.PENDING) {
            requireCanView(principal, record);
            String submitter = record.getSubmittedByUserId() == null
                    ? "another user"
                    : requireUser(principal.tenantId(), record.getSubmittedByUserId()).getFullName();
            throw conflict(
                    "DAILY_TASK_ALREADY_SUBMITTED",
                    "This task has already been submitted by " + submitter + "."
            );
        }
        requireCanContribute(principal, record);
        List<DailyTaskRecordChecklistItem> checks = recordChecklistRepository
                .findAllByTenantIdAndRecordIdOrderByPositionAsc(principal.tenantId(), recordId);
        Set<UUID> completedIds = parseChecklistItemIds(completedChecklistItemIds);
        Set<UUID> requiredIds = checks.stream()
                .map(DailyTaskRecordChecklistItem::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (checks.isEmpty() || !completedIds.equals(requiredIds)) {
            throw badRequest(
                    "DAILY_TASK_CHECKLIST_INCOMPLETE",
                    "Complete every checklist item before submitting."
            );
        }
        if (photos.size() < record.getRequiredPhotoCount()) {
            throw badRequest(
                    "DAILY_TASK_PHOTOS_INCOMPLETE",
                    "Take at least " + record.getRequiredPhotoCount() + " confirmed photos before submitting."
            );
        }
        if (photos.size() > MAX_CONFIRMED_PHOTOS_PER_TASK) {
            throw badRequest(
                    "DAILY_TASK_PHOTO_LIMIT",
                    "A Daily Task may contain at most " + MAX_CONFIRMED_PHOTOS_PER_TASK + " photos."
            );
        }

        Instant submittedAt = Instant.now();
        checks.forEach(item -> item.setCompleted(true, principal.userId(), submittedAt));
        recordChecklistRepository.saveAll(checks);

        List<DailyTaskPhoto> selectedPhotos = new ArrayList<>();
        for (MultipartFile file : photos) {
            ReportMedia media = mediaService.saveImageEntity(principal, file);
            selectedPhotos.add(new DailyTaskPhoto(
                    principal.tenantId(), recordId, media.getId(), principal.userId()
            ));
        }
        photoRepository.saveAllAndFlush(selectedPhotos);

        record.submit(principal.userId(), principal.systemRole(), submittedAt);
        auditRepository.save(new DailyTaskAuditEntry(
                principal.tenantId(), record.getTemplateId(), recordId, principal.userId(),
                "TASK_SUBMITTED",
                "Final evidence stored: " + checks.size() + " checklist item(s), "
                        + selectedPhotos.size() + " photo(s); task submitted for rating"
        ));
        return toRecordResponse(principal, record);
    }

    @Transactional
    public DailyTaskRecordResponse rate(
            AuthenticatedUser principal,
            UUID recordId,
            RateDailyTaskRequest request
    ) {
        DailyTaskRecord record = requireRecordForUpdate(principal.tenantId(), recordId);
        if (record.getStatus() != DailyTaskStatus.SUBMITTED) {
            throw conflict("DAILY_TASK_NOT_SUBMITTED", "Only a submitted task may be rated.");
        }
        if (!accessPolicy.canRate(principal.systemRole(), record.getSubmittedByRole())) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "DAILY_TASK_RATING_RANK_REQUIRED",
                    "Only a higher-ranked Manager, Head or Owner may rate this submission."
            );
        }
        record.rate(request.rating(), request.comment(), principal.userId(), Instant.now());
        auditRepository.save(new DailyTaskAuditEntry(
                principal.tenantId(), record.getTemplateId(), recordId, principal.userId(),
                "TASK_RATED", request.rating() + " star(s): " + request.comment().trim()
        ));
        return toRecordResponse(principal, record);
    }

    private void materialiseActiveTemplates(UUID tenantId, LocalDate date) {
        List<DailyTaskTemplate> templates = templateRepository
                .findAllByTenantIdAndActiveTrueOrderByTitleAsc(tenantId);
        Set<UUID> existing = recordRepository
                .findAllByTenantIdAndTaskDateOrderByTagNameAscTitleAsc(tenantId, date)
                .stream()
                .map(DailyTaskRecord::getTemplateId)
                .collect(Collectors.toSet());
        if (templates.stream().allMatch(template -> existing.contains(template.getId()))) return;

        lockTenant(tenantId);
        for (DailyTaskTemplate template : templates) {
            if (recordRepository.findByTenantIdAndTemplateIdAndTaskDate(
                    tenantId, template.getId(), date
            ).isEmpty()) {
                materialiseTemplate(template, date, requireTag(tenantId, template.getTagId()));
            }
        }
    }

    private DailyTaskRecord materialiseTemplate(
            DailyTaskTemplate template,
            LocalDate date,
            StockTag tag
    ) {
        return recordRepository.findByTenantIdAndTemplateIdAndTaskDate(
                        template.getTenantId(), template.getId(), date
                )
                .orElseGet(() -> {
                    DailyTaskRecord saved = recordRepository.saveAndFlush(
                            new DailyTaskRecord(template, date, tag.getTag())
                    );
                    List<DailyTaskTemplateChecklistItem> templateChecks = templateChecklistRepository
                            .findAllByTenantIdAndTemplateIdOrderByPositionAsc(
                                    template.getTenantId(), template.getId()
                            );
                    recordChecklistRepository.saveAll(templateChecks.stream()
                            .map(item -> new DailyTaskRecordChecklistItem(
                                    template.getTenantId(),
                                    saved.getId(),
                                    item.getPosition(),
                                    item.getDescription()
                            ))
                            .toList());
                    return saved;
                });
    }

    private void lockTenant(UUID tenantId) {
        tenantRepository.findByIdForUpdate(tenantId)
                .orElseThrow(() -> notFound("TENANT_NOT_FOUND", "Business was not found."));
    }

    private void saveTemplateChecklist(UUID tenantId, UUID templateId, List<String> items) {
        if (items == null || items.isEmpty() || items.size() > 5) {
            throw badRequest(
                    "DAILY_TASK_CHECKLIST_SIZE",
                    "A daily task requires between 1 and 5 checklist items."
            );
        }
        List<DailyTaskTemplateChecklistItem> entities = new ArrayList<>();
        for (int index = 0; index < items.size(); index++) {
            String description = items.get(index) == null ? "" : items.get(index).trim();
            if (description.isEmpty()) {
                throw badRequest(
                        "DAILY_TASK_CHECKLIST_DESCRIPTION_REQUIRED",
                        "Every checklist item needs a description."
                );
            }
            entities.add(new DailyTaskTemplateChecklistItem(
                    tenantId, templateId, index, description
            ));
        }
        templateChecklistRepository.saveAll(entities);
    }

    private DailyTaskTemplateResponse toTemplateResponse(UUID tenantId, DailyTaskTemplate template) {
        StockTag tag = requireTag(tenantId, template.getTagId());
        List<String> checks = templateChecklistRepository
                .findAllByTenantIdAndTemplateIdOrderByPositionAsc(tenantId, template.getId())
                .stream()
                .map(DailyTaskTemplateChecklistItem::getDescription)
                .toList();
        return new DailyTaskTemplateResponse(
                template.getId(),
                template.getTagId(),
                tag.getTag(),
                template.getTitle(),
                template.getInstruction(),
                template.getRequiredPhotoCount(),
                checks,
                template.isActive(),
                person(tenantId, template.getCreatedByUserId()),
                person(tenantId, template.getUpdatedByUserId()),
                template.getCreatedAt(),
                template.getUpdatedAt(),
                auditRepository
                        .findAllByTenantIdAndTemplateIdAndRecordIdIsNullOrderByOccurredAtAscIdAsc(
                                tenantId, template.getId()
                        )
                        .stream()
                        .map(entry -> toAuditResponse(tenantId, entry))
                        .toList()
        );
    }

    private DailyTaskRecordResponse toRecordResponse(
            AuthenticatedUser principal,
            DailyTaskRecord record
    ) {
        Set<UUID> assignedTagIds = principal.isOwner()
                ? Set.of()
                : assignedTagIds(principal);
        return toRecordResponses(principal, List.of(record), assignedTagIds).getFirst();
    }

    private List<DailyTaskRecordResponse> toRecordResponses(
            AuthenticatedUser principal,
            List<DailyTaskRecord> records,
            Set<UUID> assignedTagIds
    ) {
        if (records.isEmpty()) return List.of();
        UUID tenantId = principal.tenantId();
        Set<UUID> recordIds = records.stream()
                .map(DailyTaskRecord::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<UUID, List<DailyTaskRecordChecklistItem>> checksByRecord = new HashMap<>();
        for (DailyTaskRecordChecklistItem item : recordChecklistRepository
                .findAllByTenantIdAndRecordIdIn(tenantId, recordIds)) {
            checksByRecord.computeIfAbsent(item.getRecordId(), ignored -> new ArrayList<>())
                    .add(item);
        }
        checksByRecord.values().forEach(items -> items.sort(
                Comparator.comparingInt(DailyTaskRecordChecklistItem::getPosition)
                        .thenComparing(DailyTaskRecordChecklistItem::getId)
        ));

        Map<UUID, List<DailyTaskPhoto>> photosByRecord = new HashMap<>();
        for (DailyTaskPhoto photo : photoRepository
                .findAllByTenantIdAndRecordIdIn(tenantId, recordIds)) {
            photosByRecord.computeIfAbsent(photo.getRecordId(), ignored -> new ArrayList<>())
                    .add(photo);
        }
        photosByRecord.values().forEach(items -> items.sort(
                Comparator.comparing(
                                DailyTaskPhoto::getSubmittedAt,
                                Comparator.nullsLast(Comparator.naturalOrder())
                        )
                        .thenComparing(DailyTaskPhoto::getId)
        ));

        Map<UUID, List<DailyTaskAuditEntry>> activityByRecord = new HashMap<>();
        for (DailyTaskAuditEntry entry : auditRepository
                .findAllByTenantIdAndRecordIdIn(tenantId, recordIds)) {
            activityByRecord.computeIfAbsent(entry.getRecordId(), ignored -> new ArrayList<>())
                    .add(entry);
        }
        activityByRecord.values().forEach(items -> items.sort(
                Comparator.comparing(
                                DailyTaskAuditEntry::getOccurredAt,
                                Comparator.nullsLast(Comparator.naturalOrder())
                        )
                        .thenComparing(DailyTaskAuditEntry::getId)
        ));

        List<UUID> mediaIds = photosByRecord.values().stream()
                .flatMap(List::stream)
                .map(DailyTaskPhoto::getPhotoMediaId)
                .distinct()
                .toList();
        Map<UUID, ReportMedia> mediaById = mediaIds.isEmpty()
                ? Map.of()
                : mediaRepository.findAllByTenantIdAndIdIn(tenantId, mediaIds)
                        .stream()
                        .collect(Collectors.toMap(ReportMedia::getId, media -> media));

        Set<UUID> userIds = new LinkedHashSet<>();
        for (DailyTaskRecord record : records) {
            if (record.getSubmittedByUserId() != null) userIds.add(record.getSubmittedByUserId());
            if (record.getRatedByUserId() != null) userIds.add(record.getRatedByUserId());
        }
        checksByRecord.values().stream()
                .flatMap(List::stream)
                .map(DailyTaskRecordChecklistItem::getCompletedByUserId)
                .filter(java.util.Objects::nonNull)
                .forEach(userIds::add);
        photosByRecord.values().stream()
                .flatMap(List::stream)
                .map(DailyTaskPhoto::getSubmittedByUserId)
                .forEach(userIds::add);
        activityByRecord.values().stream()
                .flatMap(List::stream)
                .map(DailyTaskAuditEntry::getActorUserId)
                .forEach(userIds::add);
        Map<UUID, UserAccount> usersById = userIds.isEmpty()
                ? Map.of()
                : userRepository.findAllByTenant_IdAndIdIn(tenantId, userIds)
                        .stream()
                        .collect(Collectors.toMap(UserAccount::getId, user -> user));

        List<DailyTaskRecordResponse> responses = new ArrayList<>(records.size());
        for (DailyTaskRecord record : records) {
            List<DailyTaskRecordChecklistItem> checks = checksByRecord
                    .getOrDefault(record.getId(), List.of());
            List<DailyTaskPhoto> photos = photosByRecord
                    .getOrDefault(record.getId(), List.of());
            responses.add(toRecordResponse(
                    principal,
                    record,
                    checks,
                    photos,
                    activityByRecord.getOrDefault(record.getId(), List.of()),
                    mediaById,
                    usersById,
                    assignedTagIds
            ));
        }
        return List.copyOf(responses);
    }

    private DailyTaskRecordResponse toRecordResponse(
            AuthenticatedUser principal,
            DailyTaskRecord record,
            List<DailyTaskRecordChecklistItem> checks,
            List<DailyTaskPhoto> photos,
            List<DailyTaskAuditEntry> activity,
            Map<UUID, ReportMedia> mediaById,
            Map<UUID, UserAccount> usersById,
            Set<UUID> assignedTagIds
    ) {
        boolean requirementsMet = photos.size() >= record.getRequiredPhotoCount()
                && !checks.isEmpty()
                && checks.stream().allMatch(DailyTaskRecordChecklistItem::isCompleted);
        boolean canContribute = record.getStatus() == DailyTaskStatus.PENDING
                && (principal.isOwner() || assignedTagIds.contains(record.getTagId()));
        boolean canRate = record.getStatus() == DailyTaskStatus.SUBMITTED
                && accessPolicy.canRate(principal.systemRole(), record.getSubmittedByRole());

        List<DailyTaskChecklistItemResponse> checkResponses = checks.stream()
                .map(item -> new DailyTaskChecklistItemResponse(
                        item.getId(),
                        item.getPosition(),
                        item.getDescription(),
                        item.isCompleted(),
                        personOrNull(usersById, item.getCompletedByUserId()),
                        item.getCompletedAt()
                ))
                .toList();
        List<DailyTaskPhotoResponse> photoResponses = photos.stream()
                .map(photo -> {
                    ReportMedia media = mediaById.get(photo.getPhotoMediaId());
                    if (media == null) {
                        throw notFound(
                                    "DAILY_TASK_PHOTO_MEDIA_NOT_FOUND",
                                    "A confirmed task photo was not found."
                        );
                    }
                    return new DailyTaskPhotoResponse(
                            photo.getId(),
                            media.getStorageKey(),
                            person(usersById, photo.getSubmittedByUserId()),
                            photo.getSubmittedAt()
                    );
                })
                .toList();
        List<DailyTaskAuditResponse> activityResponses = activity.stream()
                .map(entry -> toAuditResponse(usersById, entry))
                .toList();
        return new DailyTaskRecordResponse(
                record.getId(),
                record.getTemplateId(),
                record.getTagId(),
                record.getTagName(),
                record.getTaskDate(),
                record.getTitle(),
                record.getInstruction(),
                record.getRequiredPhotoCount(),
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

    private DailyTaskOverviewResponse overviewOf(List<DailyTaskRecord> records) {
        int pending = 0;
        int submitted = 0;
        int done = 0;
        for (DailyTaskRecord record : records) {
            switch (record.getStatus()) {
                case PENDING -> pending++;
                case SUBMITTED -> submitted++;
                case DONE -> done++;
            }
        }
        return new DailyTaskOverviewResponse(records.size(), pending, submitted, done);
    }

    private Set<UUID> assignedTagIds(AuthenticatedUser principal) {
        return tagAssigneeRepository
                .findAllByTenantIdAndUserId(principal.tenantId(), principal.userId())
                .stream()
                .map(assignment -> assignment.getTagId())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private boolean canContribute(AuthenticatedUser principal, DailyTaskRecord record) {
        return principal.isOwner() || tagAssigneeRepository.existsByTenantIdAndTagIdAndUserId(
                principal.tenantId(), record.getTagId(), principal.userId()
        );
    }

    private void requireCanContribute(AuthenticatedUser principal, DailyTaskRecord record) {
        if (!canContribute(principal, record)) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "DAILY_TASK_TAG_ACCESS_REQUIRED",
                    "Only users assigned to this task's tag may contribute or submit."
            );
        }
    }

    private void requireCanView(AuthenticatedUser principal, DailyTaskRecord record) {
        if (accessPolicy.canOversee(principal.systemRole())
                || principal.isOwner()
                || principal.userId().equals(record.getSubmittedByUserId())
                || record.getTaskDate().equals(today()) && canContribute(principal, record)) {
            return;
        }
        throw new ApiException(
                HttpStatus.FORBIDDEN,
                "DAILY_TASK_VIEW_DENIED",
                "This daily task is not available to the current user."
        );
    }

    private Set<UUID> parseChecklistItemIds(String value) {
        Set<UUID> ids = new LinkedHashSet<>();
        if (value == null || value.isBlank()) return ids;
        for (String rawId : value.split(",")) {
            String itemId = rawId.trim();
            if (itemId.isEmpty()) {
                throw badRequest(
                        "DAILY_TASK_CHECKLIST_INVALID",
                        "The submitted checklist is invalid."
                );
            }
            UUID id;
            try {
                id = UUID.fromString(itemId);
            } catch (IllegalArgumentException exception) {
                throw badRequest(
                        "DAILY_TASK_CHECKLIST_INVALID",
                        "The submitted checklist is invalid."
                );
            }
            if (!ids.add(id)) {
                throw badRequest(
                        "DAILY_TASK_CHECKLIST_INVALID",
                        "The submitted checklist contains a duplicate item."
                );
            }
        }
        return ids;
    }

    private DailyTaskTemplate requireTemplate(UUID tenantId, UUID templateId) {
        return templateRepository.findByIdAndTenantId(templateId, tenantId)
                .orElseThrow(() -> notFound("DAILY_TASK_TEMPLATE_NOT_FOUND", "Daily task template was not found."));
    }

    private DailyTaskRecord requireRecord(UUID tenantId, UUID recordId) {
        return recordRepository.findByIdAndTenantId(recordId, tenantId)
                .orElseThrow(() -> notFound("DAILY_TASK_NOT_FOUND", "Daily task was not found."));
    }

    private DailyTaskRecord requireRecordForUpdate(UUID tenantId, UUID recordId) {
        return recordRepository.findByIdAndTenantIdForUpdate(recordId, tenantId)
                .orElseThrow(() -> notFound("DAILY_TASK_NOT_FOUND", "Daily task was not found."));
    }

    private StockTag requireTag(UUID tenantId, UUID tagId) {
        return tagRepository.findByIdAndTenant_Id(tagId, tenantId)
                .orElseThrow(() -> notFound("STOCK_TAG_NOT_FOUND", "The selected tag was not found."));
    }

    private UserAccount requireUser(UUID tenantId, UUID userId) {
        return userRepository.findByIdAndTenant_Id(userId, tenantId)
                .orElseThrow(() -> notFound("USER_NOT_FOUND", "User was not found."));
    }

    private DailyTaskPersonResponse person(UUID tenantId, UUID userId) {
        UserAccount user = requireUser(tenantId, userId);
        return person(user);
    }

    private DailyTaskPersonResponse person(Map<UUID, UserAccount> usersById, UUID userId) {
        UserAccount user = usersById.get(userId);
        if (user == null) {
            throw notFound("USER_NOT_FOUND", "User was not found.");
        }
        return person(user);
    }

    private DailyTaskPersonResponse person(UserAccount user) {
        return new DailyTaskPersonResponse(
                user.getId(),
                user.getFullName(),
                user.getEmployeeId(),
                user.getRole().getSystemKey()
        );
    }

    private DailyTaskAuditResponse toAuditResponse(
            UUID tenantId,
            DailyTaskAuditEntry entry
    ) {
        return new DailyTaskAuditResponse(
                entry.getId(),
                entry.getAction(),
                entry.getDetails(),
                person(tenantId, entry.getActorUserId()),
                entry.getOccurredAt()
        );
    }

    private DailyTaskAuditResponse toAuditResponse(
            Map<UUID, UserAccount> usersById,
            DailyTaskAuditEntry entry
    ) {
        return new DailyTaskAuditResponse(
                entry.getId(),
                entry.getAction(),
                entry.getDetails(),
                person(usersById, entry.getActorUserId()),
                entry.getOccurredAt()
        );
    }

    private DailyTaskPersonResponse personOrNull(UUID tenantId, UUID userId) {
        return userId == null ? null : person(tenantId, userId);
    }

    private DailyTaskPersonResponse personOrNull(
            Map<UUID, UserAccount> usersById,
            UUID userId
    ) {
        return userId == null ? null : person(usersById, userId);
    }

    private void requireManagement(AuthenticatedUser principal) {
        if (!accessPolicy.canOversee(principal.systemRole())) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "DAILY_TASK_MANAGEMENT_REQUIRED",
                    "Only Owner, Head or Manager may manage daily task templates."
            );
        }
    }

    private void validateDate(LocalDate date) {
        if (date.isAfter(today())) {
            throw badRequest("DAILY_TASK_FUTURE_DATE", "Daily tasks cannot be loaded for a future date.");
        }
    }

    private void validateDateRange(LocalDate dateFrom, LocalDate dateTo) {
        if (dateFrom.isAfter(dateTo)) {
            throw badRequest(
                    "DAILY_TASK_DATE_RANGE_INVALID",
                    "Daily task start date must not be after the end date."
            );
        }
        validateDate(dateFrom);
        validateDate(dateTo);
        if (dateFrom.plusDays(MAX_HISTORY_RANGE_DAYS - 1L).isBefore(dateTo)) {
            throw badRequest(
                    "DAILY_TASK_DATE_RANGE_TOO_LARGE",
                    "Daily task history may be loaded for a maximum of 30 days."
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
