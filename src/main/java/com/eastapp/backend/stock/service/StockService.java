package com.eastapp.backend.stock.service;

import com.eastapp.backend.common.api.PageResponse;
import com.eastapp.backend.organisation.Tenant;
import com.eastapp.backend.organisation.TenantRepository;
import com.eastapp.backend.people.UserAccount;
import com.eastapp.backend.people.UserAccountRepository;
import com.eastapp.backend.auth.security.AuthenticatedUser;
import com.eastapp.backend.common.error.ApiException;
import com.eastapp.backend.knowledge.KnowledgeSopRepository;
import com.eastapp.backend.stock.StockAuditEntry;
import com.eastapp.backend.stock.StockAuditEntryRepository;
import com.eastapp.backend.stock.StockCountSubmission;
import com.eastapp.backend.stock.StockCountSubmissionRepository;
import com.eastapp.backend.stock.StockReceiving;
import com.eastapp.backend.stock.StockReceivingItem;
import com.eastapp.backend.stock.StockReceivingRepository;
import com.eastapp.backend.stock.StockMedia;
import com.eastapp.backend.stock.StockMediaRepository;
import com.eastapp.backend.stock.StockMediaReference;
import com.eastapp.backend.stock.StockSku;
import com.eastapp.backend.stock.StockSkuRepository;
import com.eastapp.backend.stock.StockSupplier;
import com.eastapp.backend.stock.StockSupplierRepository;
import com.eastapp.backend.stock.StockTag;
import com.eastapp.backend.stock.StockTagAssignee;
import com.eastapp.backend.stock.StockTagAssigneeRepository;
import com.eastapp.backend.stock.StockTagRepository;
import com.eastapp.backend.stock.StockWorkflowStatus;
import com.eastapp.backend.stock.api.BulkReviewStockCountsResponse;
import com.eastapp.backend.stock.api.BulkReviewStockCountsRequest;
import com.eastapp.backend.stock.api.CreateStockCountRequest;
import com.eastapp.backend.stock.api.CreateStockReceivingItemRequest;
import com.eastapp.backend.stock.api.CreateStockReceivingRequest;
import com.eastapp.backend.stock.api.CreateStockSupplierRequest;
import com.eastapp.backend.stock.api.CreateStockTagRequest;
import com.eastapp.backend.stock.api.ReviewStockRecordRequest;
import com.eastapp.backend.stock.api.StockAuditEntryResponse;
import com.eastapp.backend.stock.api.StockCountSubmissionResponse;
import com.eastapp.backend.stock.api.StockReceivingResponse;
import com.eastapp.backend.stock.api.StockReviewSummaryResponse;
import com.eastapp.backend.stock.api.StockSkuResponse;
import com.eastapp.backend.stock.api.StockSnapshotResponse;
import com.eastapp.backend.stock.api.StockSupplierResponse;
import com.eastapp.backend.stock.api.StockTagAssigneeResponse;
import com.eastapp.backend.stock.api.StockTagResponse;
import com.eastapp.backend.stock.api.UpdateStockBalanceRequest;
import com.eastapp.backend.stock.api.UpdateStockTagRequest;
import com.eastapp.backend.stock.api.UpsertStockSkuRequest;
import com.eastapp.backend.tasks.TaskTemplateRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class StockService {
    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Kuala_Lumpur");
    private static final int SNAPSHOT_HISTORY_SIZE = 100;
    private static final Instant UNBOUNDED_FROM = Instant.parse("0001-01-01T00:00:00Z");
    private static final Instant UNBOUNDED_TO = Instant.parse("9999-12-31T23:59:59Z");

    private final TenantRepository tenantRepository;
    private final UserAccountRepository userAccountRepository;
    private final StockTagRepository tagRepository;
    private final StockTagAssigneeRepository tagAssigneeRepository;
    private final StockSupplierRepository supplierRepository;
    private final StockSkuRepository skuRepository;
    private final StockCountSubmissionRepository countRepository;
    private final StockReceivingRepository receivingRepository;
    private final StockAuditEntryRepository auditRepository;
    private final StockMediaRepository mediaRepository;
    private final KnowledgeSopRepository knowledgeSopRepository;
    private final TaskTemplateRepository taskTemplateRepository;

    public StockService(
            TenantRepository tenantRepository,
            UserAccountRepository userAccountRepository,
            StockTagRepository tagRepository,
            StockTagAssigneeRepository tagAssigneeRepository,
            StockSupplierRepository supplierRepository,
            StockSkuRepository skuRepository,
            StockCountSubmissionRepository countRepository,
            StockReceivingRepository receivingRepository,
            StockAuditEntryRepository auditRepository,
            StockMediaRepository mediaRepository,
            KnowledgeSopRepository knowledgeSopRepository,
            TaskTemplateRepository taskTemplateRepository
    ) {
        this.tenantRepository = tenantRepository;
        this.userAccountRepository = userAccountRepository;
        this.tagRepository = tagRepository;
        this.tagAssigneeRepository = tagAssigneeRepository;
        this.supplierRepository = supplierRepository;
        this.skuRepository = skuRepository;
        this.countRepository = countRepository;
        this.receivingRepository = receivingRepository;
        this.auditRepository = auditRepository;
        this.mediaRepository = mediaRepository;
        this.knowledgeSopRepository = knowledgeSopRepository;
        this.taskTemplateRepository = taskTemplateRepository;
    }

    @Transactional(readOnly = true)
    public StockSnapshotResponse snapshot(AuthenticatedUser principal) {
        UUID tenantId = principal.tenantId();
        List<StockTag> tags = tagRepository.findAllByTenant_IdOrderByTagAsc(tenantId);
        List<StockSku> skus = skuRepository.findAllByTenant_IdOrderByNameAsc(tenantId);
        List<StockCountSubmission> counts = countRepository
                .findAllByTenant_IdOrderByCapturedAtDesc(
                        tenantId,
                        PageRequest.of(0, SNAPSHOT_HISTORY_SIZE)
                )
                .getContent();
        List<StockReceiving> receivings = receivingRepository
                .findAllByTenant_IdOrderByCapturedAtDesc(
                        tenantId,
                        PageRequest.of(0, SNAPSHOT_HISTORY_SIZE)
                )
                .getContent();
        List<StockSku> responseSkus = new ArrayList<>(skus);
        counts.forEach(count -> responseSkus.add(count.getSku()));
        Map<UUID, String> photoPaths = skuPhotoPaths(tenantId, responseSkus);
        return new StockSnapshotResponse(
                tagResponses(tenantId, tags),
                supplierRepository.findAllByTenant_IdOrderBySupplierNameAsc(tenantId)
                        .stream().map(StockSupplierResponse::from).toList(),
                skus.stream()
                        .map(sku -> StockSkuResponse.from(sku, photoPath(sku, photoPaths)))
                        .toList(),
                counts.stream()
                        .map(count -> StockCountSubmissionResponse.from(
                                count,
                                photoPath(count.getSku(), photoPaths)
                        ))
                        .toList(),
                receivings.stream().map(StockReceivingResponse::from).toList()
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<StockTagResponse> listTags(
            AuthenticatedUser principal,
            String search,
            int page,
            int size
    ) {
        Page<StockTag> source = tagRepository.searchByTenant(
                principal.tenantId(), normaliseSearch(search), pageRequest(page, size)
        );
        return PageResponse.from(
                source,
                tagResponses(principal.tenantId(), source.getContent())
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<StockSupplierResponse> listSuppliers(
            AuthenticatedUser principal,
            String search,
            int page,
            int size
    ) {
        return PageResponse.from(
                supplierRepository.searchByTenant(
                        principal.tenantId(), normaliseSearch(search), pageRequest(page, size)
                ),
                StockSupplierResponse::from
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<StockSkuResponse> listSkus(
            AuthenticatedUser principal,
            String search,
            Boolean active,
            Boolean assigned,
            int page,
            int size
    ) {
        Page<StockSku> source = skuRepository.searchByTenant(
                principal.tenantId(), normaliseSearch(search), active, assigned,
                pageRequest(page, size)
        );
        Map<UUID, String> photoPaths = skuPhotoPaths(
                principal.tenantId(),
                source.getContent()
        );
        return PageResponse.from(
                source,
                source.getContent().stream()
                        .map(sku -> StockSkuResponse.from(sku, photoPath(sku, photoPaths)))
                        .toList()
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<StockCountSubmissionResponse> listCounts(
            AuthenticatedUser principal,
            boolean mine,
            String reviewStatus,
            LocalDate from,
            LocalDate to,
            int page,
            int size
    ) {
        DateRange range = dateRange(from, to);
        boolean filterBySubmittedBy = mine || !principal.isHead() && !principal.isManager();
        UUID submittedByUserId = principal.userId();
        String resolvedReviewStatus = reviewStatus(reviewStatus);
        Page<StockCountSubmission> source = countRepository.searchByTenant(
                principal.tenantId(),
                filterBySubmittedBy,
                submittedByUserId,
                resolvedReviewStatus != null,
                resolvedReviewStatus == null ? "" : resolvedReviewStatus,
                range.filterByFrom(),
                range.fromInclusive(),
                range.filterByTo(),
                range.toExclusive(),
                pageRequest(page, size)
        );
        Map<UUID, String> photoPaths = skuPhotoPaths(
                principal.tenantId(),
                source.getContent().stream().map(StockCountSubmission::getSku).toList()
        );
        return PageResponse.from(
                source,
                source.getContent().stream()
                        .map(count -> StockCountSubmissionResponse.from(
                                count,
                                photoPath(count.getSku(), photoPaths)
                        ))
                        .toList()
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<StockReceivingResponse> listReceivings(
            AuthenticatedUser principal,
            String reviewStatus,
            LocalDate from,
            LocalDate to,
            int page,
            int size
    ) {
        DateRange range = dateRange(from, to);
        String resolvedReviewStatus = reviewStatus(reviewStatus);
        return PageResponse.from(
                receivingRepository.searchByTenant(
                        principal.tenantId(),
                        resolvedReviewStatus != null,
                        resolvedReviewStatus == null ? "" : resolvedReviewStatus,
                        range.filterByFrom(),
                        range.fromInclusive(),
                        range.filterByTo(),
                        range.toExclusive(),
                        pageRequest(page, size)
                ),
                StockReceivingResponse::from
        );
    }

    @Transactional
    public StockTagResponse createTag(AuthenticatedUser principal, CreateStockTagRequest request) {
        String tag = request.tag().trim();
        if (tagRepository.existsByTenant_IdAndTagIgnoreCase(principal.tenantId(), tag)) {
            throw conflict("STOCK_TAG_EXISTS", "This stock tag already exists.");
        }
        Tenant tenant = tenant(principal.tenantId());
        UserAccount actor = actor(principal);
        StockTag saved = tagRepository.saveAndFlush(new StockTag(tenant, tag, actor));
        List<UserAccount> assignedUsers = replaceTagAssignees(
                principal.tenantId(), saved.getId(), request.assignedUserIds(), actor
        );
        auditRepository.save(new StockAuditEntry(
                tenant, "Tag", "Created tag", saved.getId(), saved.getTag(), principal, "")
                .addChange("Tag", "-", saved.getTag())
                .addChange("Assigned Users", "-", userLabels(assignedUsers)));
        return tagResponse(saved, assignedUsers);
    }

    @Transactional
    public StockTagResponse updateTag(
            AuthenticatedUser principal,
            UUID tagId,
            UpdateStockTagRequest request
    ) {
        StockTag tag = tag(tagId, principal.tenantId());
        String oldName = tag.getTag();
        String newName = request.tag().trim();
        if (!oldName.equalsIgnoreCase(newName)
                && tagRepository.existsByTenant_IdAndTagIgnoreCase(principal.tenantId(), newName)) {
            throw conflict("STOCK_TAG_EXISTS", "This stock tag already exists.");
        }
        UserAccount actor = actor(principal);
        List<UserAccount> oldUsers = assignedUsers(principal.tenantId(), tagId);
        tag.rename(newName, actor);
        List<UserAccount> newUsers = request.assignedUserIds() == null
                ? oldUsers
                : replaceTagAssignees(
                        principal.tenantId(), tagId, request.assignedUserIds(), actor
                );
        auditRepository.save(new StockAuditEntry(
                tag.getTenant(), "Tag", "Updated tag", tag.getId(), newName, principal, "")
                .addChange("Tag", oldName, newName)
                .addChange("Assigned Users", userLabels(oldUsers), userLabels(newUsers)));
        return tagResponse(tag, newUsers);
    }

    @Transactional
    public void deleteTag(AuthenticatedUser principal, UUID tagId) {
        StockTag tag = tag(tagId, principal.tenantId());
        boolean inUse = skuRepository.existsByTenant_IdAndTag1_Id(
                principal.tenantId(), tagId
        ) || skuRepository.existsByTenant_IdAndTag2_Id(
                principal.tenantId(), tagId
        );
        if (inUse) {
            throw conflict("STOCK_TAG_IN_USE", "This tag is assigned to an SKU and cannot be deleted.");
        }
        if (knowledgeSopRepository.existsByTenant_IdAndTag_Id(principal.tenantId(), tagId)) {
            throw conflict(
                    "STOCK_TAG_IN_USE_BY_SOP",
                    "This tag is assigned to a Knowledge SOP and cannot be deleted."
            );
        }
        if (taskTemplateRepository.existsByTenantIdAndTagId(principal.tenantId(), tagId)) {
            throw conflict(
                    "STOCK_TAG_IN_USE_BY_TASK",
                    "This tag belongs to a Task and cannot be deleted."
            );
        }
        auditRepository.save(new StockAuditEntry(
                tag.getTenant(), "Tag", "Deleted tag", tag.getId(), tag.getTag(), principal, "")
                .addChange("Tag", tag.getTag(), "Deleted"));
        tagRepository.delete(tag);
    }

    @Transactional
    public StockSupplierResponse createSupplier(
            AuthenticatedUser principal,
            CreateStockSupplierRequest request
    ) {
        if (supplierRepository.existsByTenant_IdAndSupplierNameIgnoreCase(
                principal.tenantId(), request.supplierName().trim())) {
            throw conflict("STOCK_SUPPLIER_EXISTS", "This supplier already exists.");
        }
        Tenant tenant = tenant(principal.tenantId());
        UserAccount actor = actor(principal);
        StockSupplier supplier = supplierRepository.save(new StockSupplier(
                tenant,
                request.supplierName(), request.supplierItem(), request.contactPerson(),
                request.phone(), request.address(), request.notes(), request.unit(),
                request.recommendedPurchaseAmount(), request.recommendedPurchaseFrequency(),
                request.pricingPerUnit(), request.minimumBalanceValue(),
                request.maximumBalanceValue(), request.currentBalanceValue(), actor
        ));
        auditRepository.save(new StockAuditEntry(
                tenant, "Supplier", "Created supplier", supplier.getId(),
                supplier.getSupplierName(), principal, "")
                .addChange("Supplier Name", "-", supplier.getSupplierName())
                .addChange("Supplier Item", "-", supplier.getSupplierItem())
                .addChange("Current Balance", "-", supplier.getCurrentBalanceValue()));
        return StockSupplierResponse.from(supplier);
    }

    @Transactional
    public StockSupplierResponse updateSupplier(
            AuthenticatedUser principal,
            UUID supplierId,
            CreateStockSupplierRequest request
    ) {
        StockSupplier supplier = supplier(supplierId, principal.tenantId());
        String previousName = supplier.getSupplierName();
        if (!previousName.equalsIgnoreCase(request.supplierName().trim())
                && supplierRepository.existsByTenant_IdAndSupplierNameIgnoreCase(
                        principal.tenantId(), request.supplierName().trim())) {
            throw conflict("STOCK_SUPPLIER_EXISTS", "This supplier already exists.");
        }
        supplier.update(
                request.supplierName(), request.supplierItem(), request.contactPerson(),
                request.phone(), request.address(), request.notes(), request.unit(),
                request.recommendedPurchaseAmount(), request.recommendedPurchaseFrequency(),
                request.pricingPerUnit(), request.minimumBalanceValue(),
                request.maximumBalanceValue(), request.currentBalanceValue(), actor(principal)
        );
        auditRepository.save(new StockAuditEntry(
                supplier.getTenant(), "Supplier", "Edited supplier", supplier.getId(),
                supplier.getSupplierName(), principal, "")
                .addChange("Supplier Name", previousName, supplier.getSupplierName()));
        return StockSupplierResponse.from(supplier);
    }

    @Transactional
    public void deleteSupplier(AuthenticatedUser principal, UUID supplierId) {
        StockSupplier supplier = supplier(supplierId, principal.tenantId());
        boolean inUse = skuRepository.existsByTenant_IdAndSuppliers_Id(
                principal.tenantId(), supplierId)
                || receivingRepository.existsByTenant_IdAndSupplier_Id(
                principal.tenantId(), supplierId);
        if (inUse) {
            throw conflict(
                    "STOCK_SUPPLIER_IN_USE",
                    "This supplier is assigned to an SKU or receiving record and cannot be deleted."
            );
        }
        auditRepository.save(new StockAuditEntry(
                supplier.getTenant(), "Supplier", "Deleted supplier", supplier.getId(),
                supplier.getSupplierName(), principal, "")
                .addChange("Supplier", supplier.getSupplierName(), "Deleted"));
        supplierRepository.delete(supplier);
    }

    @Transactional
    public StockSupplierResponse updateSupplierBalance(
            AuthenticatedUser principal,
            UUID supplierId,
            UpdateStockBalanceRequest request
    ) {
        StockSupplier supplier = supplier(supplierId, principal.tenantId());
        BigDecimal previous = supplier.getCurrentBalanceValue();
        supplier.updateBalance(request.balance(), actor(principal));
        auditRepository.save(new StockAuditEntry(
                supplier.getTenant(), "Supplier Balance", "Updated supplier balance",
                supplier.getId(), supplier.getSupplierName(), principal, "")
                .addChange("Current Balance", previous, request.balance()));
        return StockSupplierResponse.from(supplier);
    }

    @Transactional
    public StockSkuResponse createSku(AuthenticatedUser principal, UpsertStockSkuRequest request) {
        if (skuRepository.existsByTenant_IdAndNameIgnoreCase(principal.tenantId(), request.name().trim())) {
            throw conflict("STOCK_SKU_EXISTS", "This SKU already exists.");
        }
        StockMedia thumbnail = skuThumbnail(principal, request.photoPath());
        Tenant tenant = tenant(principal.tenantId());
        UserAccount actor = actor(principal);
        StockTag tag1 = tag(request.tag1Id(), principal.tenantId());
        StockTag tag2 = tag(request.tag2Id(), principal.tenantId());
        String photoPath = stockPhotoPath(request.photoPath());
        StockSku sku = skuRepository.save(new StockSku(
                tenant, request.name(), tag1, tag2, request.unit(),
                request.minimumBalanceValue(), request.maximumBalanceValue(),
                request.currentBalanceValue(), request.recoveryPercent(),
                request.minimumPriceRm(), request.maximumPriceRm(),
                suppliers(principal.tenantId(), request.supplierIds()),
                thumbnail, request.assignedStaffNames(),
                request.receivingChecklist(), request.stockCheckFrequencyDays(),
                parseResetTime(request.resetTime()), request.active(), request.coolingPeriod(), actor
        ));
        auditRepository.save(new StockAuditEntry(
                tenant, "SKU", "Created SKU", sku.getId(), sku.getName(), principal, "")
                .addChange("Name", "-", sku.getName())
                .addChange("Unit", "-", sku.getUnit())
                .addChange("Current Balance", "-", sku.getCurrentBalanceValue()));
        return StockSkuResponse.from(sku, photoPath);
    }

    @Transactional
    public StockSkuResponse updateSku(
            AuthenticatedUser principal,
            UUID skuId,
            UpsertStockSkuRequest request
    ) {
        StockSku sku = sku(skuId, principal.tenantId());
        String oldName = sku.getName();
        BigDecimal oldBalance = sku.getCurrentBalanceValue();
        String oldPhotoPath = photoPath(
                sku,
                skuPhotoPaths(principal.tenantId(), List.of(sku))
        );
        if (!oldName.equalsIgnoreCase(request.name().trim())
                && skuRepository.existsByTenant_IdAndNameIgnoreCase(principal.tenantId(), request.name().trim())) {
            throw conflict("STOCK_SKU_EXISTS", "This SKU already exists.");
        }
        boolean keepThumbnail = request.photoPath() == null || request.photoPath().isBlank();
        StockMedia thumbnail = keepThumbnail
                ? sku.getThumbnailMedia()
                : skuThumbnail(principal, request.photoPath());
        String newPhotoPath = keepThumbnail
                ? oldPhotoPath
                : stockPhotoPath(request.photoPath());
        StockTag tag1 = tag(request.tag1Id(), principal.tenantId());
        StockTag tag2 = tag(request.tag2Id(), principal.tenantId());
        sku.update(
                request.name(), tag1, tag2, request.unit(),
                request.minimumBalanceValue(), request.maximumBalanceValue(),
                request.currentBalanceValue(), request.recoveryPercent(),
                request.minimumPriceRm(), request.maximumPriceRm(),
                suppliers(principal.tenantId(), request.supplierIds()),
                thumbnail, request.assignedStaffNames(),
                request.receivingChecklist(), request.stockCheckFrequencyDays(),
                parseResetTime(request.resetTime()), request.active(), request.coolingPeriod(),
                actor(principal)
        );
        auditRepository.save(new StockAuditEntry(
                sku.getTenant(), "SKU", "Edited SKU", sku.getId(), sku.getName(), principal, "")
                .addChange("Name", oldName, sku.getName())
                .addChange("Photo", oldPhotoPath, newPhotoPath)
                .addChange("Current Balance", oldBalance, sku.getCurrentBalanceValue()));
        return StockSkuResponse.from(sku, newPhotoPath);
    }

    @Transactional
    public StockSkuResponse updateSkuBalance(
            AuthenticatedUser principal,
            UUID skuId,
            UpdateStockBalanceRequest request
    ) {
        StockSku sku = sku(skuId, principal.tenantId());
        BigDecimal previous = sku.getCurrentBalanceValue();
        sku.updateBalance(request.balance(), actor(principal));
        auditRepository.save(new StockAuditEntry(
                sku.getTenant(), "SKU Balance", "Updated balance", sku.getId(),
                sku.getName(), principal, "")
                .addChange("Current Balance", previous, request.balance()));
        return StockSkuResponse.from(
                sku,
                photoPath(sku, skuPhotoPaths(principal.tenantId(), List.of(sku)))
        );
    }

    @Transactional
    public StockCountSubmissionResponse createCount(
            AuthenticatedUser principal,
            CreateStockCountRequest request
    ) {
        StockSku sku = sku(request.skuId(), principal.tenantId());
        UserAccount actor = actor(principal);
        if (!principal.isHead() && !principal.isManager()
                && !sku.getAssignedStaffNames().isEmpty()
                && !sku.getAssignedStaffNames().contains(actor.getFullName())) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "STOCK_SKU_NOT_ASSIGNED",
                    "This SKU is not assigned to the current user."
            );
        }
        Instant cycleStartedAt = countCycleStartedAt(sku, Instant.now());
        if (countRepository.existsByTenant_IdAndSku_IdAndCountCycleStartedAtAndReviewStatusNot(
                principal.tenantId(),
                sku.getId(),
                cycleStartedAt,
                StockWorkflowStatus.PENDING.name()
        )) {
            throw conflict(
                    "STOCK_COUNT_ALREADY_SUBMITTED",
                    "This SKU has already been counted for the current daily cycle."
            );
        }
        BigDecimal previous = sku.getCurrentBalanceValue();
        sku.updateBalance(request.currentBalanceValue(), actor);
        StockCountSubmission submission = countRepository.save(new StockCountSubmission(
                sku.getTenant(), sku, actor, request.capturedAt(), cycleStartedAt,
                request.stockPhotoName(), request.invoicePhotoName(), previous,
                request.currentBalanceValue(), request.checkedItems(), request.remarks()
        ));
        auditRepository.save(new StockAuditEntry(
                sku.getTenant(), "Stock Count", "Submitted daily count", sku.getId(),
                sku.getName(), principal, "")
                .addChange("Current Balance", previous, request.currentBalanceValue())
                .addChange("Review Status", "-", submission.getReviewStatus()));
        return StockCountSubmissionResponse.from(
                submission,
                photoPath(sku, skuPhotoPaths(principal.tenantId(), List.of(sku)))
        );
    }

    @Transactional
    public StockCountSubmissionResponse reviewCount(
            AuthenticatedUser principal,
            UUID submissionId,
            ReviewStockRecordRequest request
    ) {
        StockCountSubmission submission = countRepository
                .findByIdAndTenant_Id(submissionId, principal.tenantId())
                .orElseThrow(() -> notFound("STOCK_COUNT_NOT_FOUND", "Stock count not found."));
        String previous = submission.getReviewStatus();
        if (!"Pending Review".equals(previous)) {
            throw conflict("STOCK_COUNT_ALREADY_REVIEWED", "This stock count has already been reviewed.");
        }
        submission.review(request.status(), request.note(), actor(principal));
        auditRepository.save(new StockAuditEntry(
                submission.getTenant(), "Stock Count", "Reviewed daily count",
                submission.getSku().getId(), submission.getSku().getName(), principal, request.note())
                .addChange("Review Status", previous, request.status()));
        return StockCountSubmissionResponse.from(
                submission,
                photoPath(
                        submission.getSku(),
                        skuPhotoPaths(principal.tenantId(), List.of(submission.getSku()))
                )
        );
    }

    @Transactional
    public BulkReviewStockCountsResponse bulkReviewCounts(
            AuthenticatedUser principal,
            BulkReviewStockCountsRequest request
    ) {
        List<UUID> requestedIds = request.submissionIds();
        LinkedHashSet<UUID> uniqueIds = new LinkedHashSet<>(requestedIds);
        if (uniqueIds.size() != requestedIds.size()) {
            throw badRequest(
                    "STOCK_COUNT_DUPLICATE_REVIEW_ID",
                    "Each daily count can be selected only once."
            );
        }

        List<StockCountSubmission> found = countRepository.findAllByTenant_IdAndIdIn(
                principal.tenantId(),
                List.copyOf(uniqueIds)
        );
        if (found.size() != uniqueIds.size()) {
            throw notFound(
                    "STOCK_COUNT_NOT_FOUND",
                    "One or more selected daily counts were not found."
            );
        }

        Map<UUID, StockCountSubmission> byId = new LinkedHashMap<>();
        found.forEach(item -> byId.put(item.getId(), item));
        List<StockCountSubmission> ordered = requestedIds.stream()
                .map(byId::get)
                .toList();

        for (StockCountSubmission submission : ordered) {
            if (!"Pending Review".equals(submission.getReviewStatus())) {
                throw conflict(
                        "STOCK_COUNT_ALREADY_REVIEWED",
                        "At least one selected daily count has already been reviewed. No records were changed."
                );
            }
        }

        UserAccount reviewer = actor(principal);
        String note = request.note() == null ? "" : request.note().trim();
        Map<UUID, String> photoPaths = skuPhotoPaths(
                principal.tenantId(),
                ordered.stream().map(StockCountSubmission::getSku).toList()
        );
        List<StockCountSubmissionResponse> responses = new ArrayList<>();
        for (StockCountSubmission submission : ordered) {
            String previous = submission.getReviewStatus();
            submission.review(request.status(), note, reviewer);
            auditRepository.save(new StockAuditEntry(
                    submission.getTenant(),
                    "Stock Count",
                    "Bulk reviewed daily count",
                    submission.getSku().getId(),
                    submission.getSku().getName(),
                    principal,
                    note
            ).addChange("Review Status", previous, request.status()));
            responses.add(StockCountSubmissionResponse.from(
                    submission,
                    photoPath(submission.getSku(), photoPaths)
            ));
        }

        return new BulkReviewStockCountsResponse(
                responses.size(),
                List.copyOf(responses)
        );
    }

    @Transactional
    public StockReceivingResponse createReceiving(
            AuthenticatedUser principal,
            CreateStockReceivingRequest request
    ) {
        StockSupplier supplier = supplier(request.supplierId(), principal.tenantId());
        requireReceivingPhoto(principal.tenantId(), request.invoicePhotoName(), "Invoice");
        requireReceivingPhoto(principal.tenantId(), request.goodsPhotoName(), "Goods received");
        UserAccount actor = actor(principal);
        StockReceiving receiving = new StockReceiving(
                supplier.getTenant(), supplier, actor, request.capturedAt(),
                request.invoicePhotoName(), request.goodsPhotoName()
        );
        Set<UUID> receivedSkuIds = new LinkedHashSet<>();
        for (CreateStockReceivingItemRequest itemRequest : request.items()) {
            if (!receivedSkuIds.add(itemRequest.skuId())) {
                throw badRequest(
                        "STOCK_RECEIVING_DUPLICATE_SKU",
                        "Each SKU can appear only once in a receiving submission."
                );
            }
        }
        Map<UUID, StockSku> skusById = skuRepository
                .findAllByTenant_IdAndIdIn(principal.tenantId(), receivedSkuIds)
                .stream()
                .collect(Collectors.toMap(StockSku::getId, item -> item));
        if (skusById.size() != receivedSkuIds.size()) {
            throw notFound("STOCK_SKU_NOT_FOUND", "One or more selected SKUs were not found.");
        }
        for (CreateStockReceivingItemRequest itemRequest : request.items()) {
            StockSku sku = skusById.get(itemRequest.skuId());
            if (sku.getSuppliers().stream().noneMatch(item -> item.getId().equals(supplier.getId()))) {
                throw badRequest(
                        "STOCK_RECEIVING_SUPPLIER_MISMATCH",
                        "The selected SKU is not assigned to this supplier."
                );
            }
            BigDecimal previous = sku.getCurrentBalanceValue();
            BigDecimal next = previous.add(itemRequest.receivedQuantity());
            sku.updateBalance(next, actor);
            receiving.addItem(new StockReceivingItem(
                    sku, itemRequest.invoiceQuantity(), itemRequest.receivedQuantity(),
                    itemRequest.condition(), itemRequest.note()
            ));
        }
        StockReceiving saved = receivingRepository.save(receiving);
        auditRepository.save(new StockAuditEntry(
                supplier.getTenant(), "Receiving", "Submitted receiving", saved.getId(),
                supplier.getSupplierName(), principal, "")
                .addChange("Supplier", "-", supplier.getSupplierName())
                .addChange("Items", "-", saved.getItems().size())
                .addChange("Review Status", "-", saved.getReviewStatus()));
        return StockReceivingResponse.from(saved);
    }

    @Transactional
    public StockReceivingResponse reviewReceiving(
            AuthenticatedUser principal,
            UUID receivingId,
            ReviewStockRecordRequest request
    ) {
        StockReceiving receiving = receivingRepository
                .findByIdAndTenant_Id(receivingId, principal.tenantId())
                .orElseThrow(() -> notFound("STOCK_RECEIVING_NOT_FOUND", "Receiving record not found."));
        String previous = receiving.getReviewStatus();
        if (!"Pending Review".equals(previous)) {
            throw conflict("STOCK_RECEIVING_ALREADY_REVIEWED", "This receiving record has already been reviewed.");
        }
        receiving.review(request.status(), request.note(), actor(principal));
        auditRepository.save(new StockAuditEntry(
                receiving.getTenant(), "Receiving", "Reviewed receiving", receiving.getId(),
                receiving.getSupplier().getSupplierName(), principal, request.note())
                .addChange("Review Status", previous, request.status()));
        return StockReceivingResponse.from(receiving);
    }

    @Transactional(readOnly = true)
    public PageResponse<StockAuditEntryResponse> audit(
            AuthenticatedUser principal,
            LocalDate from,
            LocalDate to,
            boolean mine,
            int page,
            int size
    ) {
        LocalDate resolvedFrom = from == null ? LocalDate.now(ZONE_ID).minusDays(29) : from;
        LocalDate resolvedTo = to == null ? LocalDate.now(ZONE_ID) : to;
        if (resolvedTo.isBefore(resolvedFrom)) {
            throw badRequest("INVALID_DATE_RANGE", "The audit end date must not be before the start date.");
        }
        Instant fromInclusive = resolvedFrom.atStartOfDay(ZONE_ID).toInstant();
        Instant toExclusive = resolvedTo.plusDays(1).atStartOfDay(ZONE_ID).toInstant();
        boolean currentUserOnly = mine || !principal.isHead();
        if (currentUserOnly) {
            return PageResponse.from(
                    auditRepository.findAllByTenant_IdAndActorEmployeeIdAndCapturedAtGreaterThanEqualAndCapturedAtLessThanOrderByCapturedAtDesc(
                            principal.tenantId(),
                            principal.employeeId(),
                            fromInclusive,
                            toExclusive,
                            pageRequest(page, size)
                    ),
                    StockAuditEntryResponse::from
            );
        }
        return PageResponse.from(
                auditRepository.findAllByTenant_IdAndCapturedAtGreaterThanEqualAndCapturedAtLessThanOrderByCapturedAtDesc(
                        principal.tenantId(), fromInclusive, toExclusive,
                        pageRequest(page, size)
                ),
                StockAuditEntryResponse::from
        );
    }

    @Transactional(readOnly = true)
    public StockReviewSummaryResponse todayReviewSummary(AuthenticatedUser principal) {
        LocalDate today = LocalDate.now(ZONE_ID);
        Instant fromInclusive = today.atStartOfDay(ZONE_ID).toInstant();
        Instant toExclusive = today.plusDays(1).atStartOfDay(ZONE_ID).toInstant();

        long countTotal = countRepository
                .countByTenant_IdAndCapturedAtGreaterThanEqualAndCapturedAtLessThan(
                        principal.tenantId(), fromInclusive, toExclusive
                );
        long receivingTotal = receivingRepository
                .countByTenant_IdAndCapturedAtGreaterThanEqualAndCapturedAtLessThan(
                        principal.tenantId(), fromInclusive, toExclusive
                );
        long countPending = countRepository
                .countByTenant_IdAndReviewStatusAndCapturedAtGreaterThanEqualAndCapturedAtLessThan(
                        principal.tenantId(),
                        StockWorkflowStatus.SUBMITTED.name(),
                        fromInclusive,
                        toExclusive
                );
        long receivingPending = receivingRepository
                .countByTenant_IdAndReviewStatusAndCapturedAtGreaterThanEqualAndCapturedAtLessThan(
                        principal.tenantId(),
                        StockWorkflowStatus.SUBMITTED.name(),
                        fromInclusive,
                        toExclusive
                );

        long total = countTotal + receivingTotal;
        long pending = countPending + receivingPending;
        return new StockReviewSummaryResponse(pending, total - pending, total);
    }

    private static PageRequest pageRequest(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
    }

    private static String normaliseSearch(String search) {
        return search == null ? "" : search.trim();
    }

    private static String reviewStatus(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return StockWorkflowStatus.canonicalFilter(value);
        } catch (IllegalArgumentException exception) {
            throw badRequest(
                    "INVALID_REVIEW_STATUS",
                    "Review status must be Pending Review, Approved or Rejected."
            );
        }
    }

    private static DateRange dateRange(LocalDate from, LocalDate to) {
        if (from == null && to == null) {
            return new DateRange(false, UNBOUNDED_FROM, false, UNBOUNDED_TO);
        }
        LocalDate resolvedFrom = from == null ? to : from;
        LocalDate resolvedTo = to == null ? from : to;
        if (resolvedTo.isBefore(resolvedFrom)) {
            throw badRequest("INVALID_DATE_RANGE", "The end date must not be before the start date.");
        }
        return new DateRange(
                true,
                resolvedFrom.atStartOfDay(ZONE_ID).toInstant(),
                true,
                resolvedTo.plusDays(1).atStartOfDay(ZONE_ID).toInstant()
        );
    }

    private record DateRange(
            boolean filterByFrom,
            Instant fromInclusive,
            boolean filterByTo,
            Instant toExclusive
    ) {}

    private Tenant tenant(UUID tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> notFound("TENANT_NOT_FOUND", "Tenant not found."));
    }

    private UserAccount actor(AuthenticatedUser principal) {
        return userAccountRepository.findByIdAndTenant_Id(principal.userId(), principal.tenantId())
                .orElseThrow(() -> notFound("USER_NOT_FOUND", "User not found."));
    }

    private StockTag tag(UUID id, UUID tenantId) {
        if (id == null) return null;
        return tagRepository.findByIdAndTenant_Id(id, tenantId)
                .orElseThrow(() -> notFound("STOCK_TAG_NOT_FOUND", "Stock tag not found."));
    }

    private List<StockTagResponse> tagResponses(UUID tenantId, List<StockTag> tags) {
        Map<UUID, List<UserAccount>> usersByTag = assignedUsersByTag(
                tenantId,
                tags.stream().map(StockTag::getId).toList()
        );
        return tags.stream()
                .map(tag -> tagResponse(
                        tag,
                        usersByTag.getOrDefault(tag.getId(), List.of())
                ))
                .toList();
    }

    private StockTagResponse tagResponse(StockTag tag, List<UserAccount> users) {
        return StockTagResponse.from(
                tag,
                users.stream()
                        .map(user -> new StockTagAssigneeResponse(
                                user.getId(),
                                user.getFullName(),
                                user.getEmployeeId(),
                                user.getRole().getSystemKey()
                        ))
                        .toList()
        );
    }

    private List<UserAccount> assignedUsers(UUID tenantId, UUID tagId) {
        return assignedUsersByTag(tenantId, List.of(tagId))
                .getOrDefault(tagId, List.of());
    }

    private Map<UUID, List<UserAccount>> assignedUsersByTag(
            UUID tenantId,
            Collection<UUID> tagIds
    ) {
        Set<UUID> uniqueTagIds = tagIds.stream()
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (uniqueTagIds.isEmpty()) return Map.of();
        List<StockTagAssignee> assignments = tagAssigneeRepository
                .findAllByTenantIdAndTagIdIn(tenantId, uniqueTagIds);
        Set<UUID> userIds = assignments.stream()
                .map(StockTagAssignee::getUserId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<UUID, UserAccount> usersById = userIds.isEmpty()
                ? Map.of()
                : userAccountRepository.findAllByTenant_IdAndIdIn(tenantId, userIds)
                        .stream()
                        .collect(Collectors.toMap(UserAccount::getId, user -> user));
        Map<UUID, List<UserAccount>> result = new LinkedHashMap<>();
        for (StockTagAssignee assignment : assignments) {
            UserAccount user = usersById.get(assignment.getUserId());
            if (user != null) {
                result.computeIfAbsent(assignment.getTagId(), ignored -> new ArrayList<>())
                        .add(user);
            }
        }
        result.values().forEach(users -> users.sort((left, right) -> {
            int byName = left.getFullName().compareToIgnoreCase(right.getFullName());
            return byName != 0
                    ? byName
                    : left.getEmployeeId().compareToIgnoreCase(right.getEmployeeId());
        }));
        return result;
    }

    private List<UserAccount> replaceTagAssignees(
            UUID tenantId,
            UUID tagId,
            List<UUID> requestedUserIds,
            UserAccount actor
    ) {
        LinkedHashSet<UUID> uniqueIds = new LinkedHashSet<>(
                requestedUserIds == null ? List.of() : requestedUserIds
        );
        Map<UUID, UserAccount> usersById = uniqueIds.isEmpty()
                ? Map.of()
                : userAccountRepository.findAllByTenant_IdAndIdIn(tenantId, uniqueIds)
                        .stream()
                        .collect(Collectors.toMap(UserAccount::getId, user -> user));
        if (usersById.size() != uniqueIds.size()) {
            throw notFound(
                    "TAG_ASSIGNEE_NOT_FOUND",
                    "One or more selected users were not found in this business."
            );
        }
        List<UserAccount> users = new ArrayList<>(uniqueIds.size());
        for (UUID userId : uniqueIds) {
            UserAccount user = usersById.get(userId);
            if (!user.isActive() || !user.getRole().isActive()) {
                throw badRequest(
                        "TAG_ASSIGNEE_INACTIVE",
                        "Only active users may be assigned to a tag."
                );
            }
            users.add(user);
        }

        List<StockTagAssignee> existingAssignments = tagAssigneeRepository
                .findAllByTenantIdAndTagIdOrderByCreatedAtAsc(tenantId, tagId);
        Set<UUID> existingUserIds = existingAssignments.stream()
                .map(StockTagAssignee::getUserId)
                .collect(Collectors.toSet());
        List<StockTagAssignee> removedAssignments = existingAssignments.stream()
                .filter(assignment -> !uniqueIds.contains(assignment.getUserId()))
                .toList();
        if (!removedAssignments.isEmpty()) {
            tagAssigneeRepository.deleteAllInBatch(removedAssignments);
        }
        List<UserAccount> addedUsers = users.stream()
                .filter(user -> !existingUserIds.contains(user.getId()))
                .toList();
        if (!addedUsers.isEmpty()) {
            tagAssigneeRepository.saveAllAndFlush(addedUsers.stream()
                    .map(user -> new StockTagAssignee(
                            tenantId, tagId, user.getId(), actor.getId()
                    ))
                    .toList());
        }
        users.sort((left, right) -> {
            int byName = left.getFullName().compareToIgnoreCase(right.getFullName());
            return byName != 0
                    ? byName
                    : left.getEmployeeId().compareToIgnoreCase(right.getEmployeeId());
        });
        return List.copyOf(users);
    }

    private static String userLabels(List<UserAccount> users) {
        if (users == null || users.isEmpty()) return "None";
        List<String> labels = users.stream()
                .map(user -> user.getFullName() + " (" + user.getEmployeeId() + ")")
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
        String visible = String.join(", ", labels.stream().limit(5).toList());
        return labels.size() <= 5
                ? visible
                : visible + " (and " + (labels.size() - 5) + " more; "
                        + labels.size() + " total)";
    }

    private StockSupplier supplier(UUID id, UUID tenantId) {
        return supplierRepository.findByIdAndTenant_Id(id, tenantId)
                .orElseThrow(() -> notFound("STOCK_SUPPLIER_NOT_FOUND", "Supplier not found."));
    }

    private StockSku sku(UUID id, UUID tenantId) {
        return skuRepository.findByIdAndTenant_Id(id, tenantId)
                .orElseThrow(() -> notFound("STOCK_SKU_NOT_FOUND", "SKU not found."));
    }

    private Set<StockSupplier> suppliers(UUID tenantId, List<UUID> ids) {
        if (ids == null || ids.isEmpty()) return Set.of();
        LinkedHashSet<UUID> uniqueIds = new LinkedHashSet<>(ids);
        Map<UUID, StockSupplier> found = supplierRepository
                .findAllByTenant_IdAndIdIn(tenantId, uniqueIds)
                .stream()
                .collect(Collectors.toMap(StockSupplier::getId, item -> item));
        if (found.size() != uniqueIds.size()) {
            throw notFound("STOCK_SUPPLIER_NOT_FOUND", "One or more suppliers were not found.");
        }
        Set<StockSupplier> result = new LinkedHashSet<>();
        for (UUID id : uniqueIds) result.add(found.get(id));
        return result;
    }

    private Map<UUID, String> skuPhotoPaths(UUID tenantId, Collection<StockSku> skus) {
        Set<UUID> mediaIds = skus.stream()
                .map(StockSku::getThumbnailMediaId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (mediaIds.isEmpty()) return Map.of();
        return mediaRepository.findAllReferencesByTenantIdAndIdIn(tenantId, mediaIds)
                .stream()
                .collect(Collectors.toMap(
                        StockMediaReference::id,
                        StockMediaReference::photoPath
                ));
    }

    private static String photoPath(StockSku sku, Map<UUID, String> photoPaths) {
        return photoPaths.getOrDefault(sku.getThumbnailMediaId(), "");
    }

    private void requireReceivingPhoto(UUID tenantId, String storageKey, String label) {
        String value = storageKey == null ? "" : storageKey.trim();
        if (value.isEmpty()) {
            throw badRequest(
                    "STOCK_RECEIVING_PHOTO_REQUIRED",
                    label + " photo is required. Capture and upload it first."
            );
        }
        if (mediaRepository.findReferenceByTenantIdAndStorageKey(tenantId, value).isEmpty()) {
            throw badRequest(
                    "STOCK_RECEIVING_PHOTO_NOT_FOUND",
                    label + " photo was not found. Capture it again."
            );
        }
    }

    private StockMedia skuThumbnail(AuthenticatedUser principal, String storageKey) {
        String value = storageKey == null ? "" : storageKey.trim();
        if (value.isEmpty()) {
            throw badRequest("STOCK_THUMBNAIL_REQUIRED", "Take and upload a stock thumbnail first.");
        }
        StockMediaReference media = mediaRepository
                .findReferenceByTenantIdAndStorageKey(principal.tenantId(), value)
                .orElseThrow(() -> badRequest(
                        "STOCK_THUMBNAIL_NOT_FOUND",
                        "The uploaded stock thumbnail was not found."
                ));
        return mediaRepository.getReferenceById(media.id());
    }

    private static String stockPhotoPath(String storageKey) {
        String value = storageKey == null ? "" : storageKey.trim();
        return value.startsWith(StockMedia.SKU_IMPORT_PLACEHOLDER_PREFIX) ? "" : value;
    }

    private static Instant countCycleStartedAt(StockSku sku, Instant now) {
        ZonedDateTime localNow = now.atZone(ZONE_ID);
        ZonedDateTime cycleStart = localNow.toLocalDate().atTime(sku.getResetTime()).atZone(ZONE_ID);
        if (localNow.isBefore(cycleStart)) cycleStart = cycleStart.minusDays(1);
        return cycleStart.toInstant();
    }

    private static LocalTime parseResetTime(String value) {
        try {
            return LocalTime.parse(value.trim());
        } catch (DateTimeParseException error) {
            throw badRequest("INVALID_RESET_TIME", "Reset time must use HH:mm format.");
        }
    }

    private static ApiException notFound(String code, String message) {
        return new ApiException(HttpStatus.NOT_FOUND, code, message);
    }
    private static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }
    private static ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }
}
