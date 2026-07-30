package com.eastapp.backend.stock.service;

import com.eastapp.backend.common.api.PageResponse;
import com.eastapp.backend.organisation.Tenant;
import com.eastapp.backend.organisation.TenantRepository;
import com.eastapp.backend.people.SystemRole;
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
import com.eastapp.backend.stock.StockSku;
import com.eastapp.backend.stock.StockSkuRepository;
import com.eastapp.backend.stock.StockSupplier;
import com.eastapp.backend.stock.StockSupplierRepository;
import com.eastapp.backend.stock.StockTag;
import com.eastapp.backend.stock.StockTagRepository;
import com.eastapp.backend.stock.api.CopyStockSkusRequest;
import com.eastapp.backend.stock.api.BulkReviewStockCountsResponse;
import com.eastapp.backend.stock.api.BulkReviewStockCountsRequest;
import com.eastapp.backend.stock.api.CopyStockSkusResponse;
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
import com.eastapp.backend.stock.api.StockTagResponse;
import com.eastapp.backend.stock.api.UpdateStockBalanceRequest;
import com.eastapp.backend.stock.api.UpdateStockTagRequest;
import com.eastapp.backend.stock.api.UpsertStockSkuRequest;
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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class StockService {
    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Kuala_Lumpur");
    private static final int SNAPSHOT_HISTORY_SIZE = 100;
    private static final Instant UNBOUNDED_FROM = Instant.parse("0001-01-01T00:00:00Z");
    private static final Instant UNBOUNDED_TO = Instant.parse("9999-12-31T23:59:59Z");

    private final TenantRepository tenantRepository;
    private final UserAccountRepository userAccountRepository;
    private final StockTagRepository tagRepository;
    private final StockSupplierRepository supplierRepository;
    private final StockSkuRepository skuRepository;
    private final StockCountSubmissionRepository countRepository;
    private final StockReceivingRepository receivingRepository;
    private final StockAuditEntryRepository auditRepository;
    private final StockMediaRepository mediaRepository;
    private final KnowledgeSopRepository knowledgeSopRepository;

    public StockService(
            TenantRepository tenantRepository,
            UserAccountRepository userAccountRepository,
            StockTagRepository tagRepository,
            StockSupplierRepository supplierRepository,
            StockSkuRepository skuRepository,
            StockCountSubmissionRepository countRepository,
            StockReceivingRepository receivingRepository,
            StockAuditEntryRepository auditRepository,
            StockMediaRepository mediaRepository,
            KnowledgeSopRepository knowledgeSopRepository
    ) {
        this.tenantRepository = tenantRepository;
        this.userAccountRepository = userAccountRepository;
        this.tagRepository = tagRepository;
        this.supplierRepository = supplierRepository;
        this.skuRepository = skuRepository;
        this.countRepository = countRepository;
        this.receivingRepository = receivingRepository;
        this.auditRepository = auditRepository;
        this.mediaRepository = mediaRepository;
        this.knowledgeSopRepository = knowledgeSopRepository;
    }

    @Transactional(readOnly = true)
    public StockSnapshotResponse snapshot(AuthenticatedUser principal) {
        UUID tenantId = principal.tenantId();
        return new StockSnapshotResponse(
                tagRepository.findAllByTenant_IdOrderByTagAsc(tenantId)
                        .stream().map(StockTagResponse::from).toList(),
                supplierRepository.findAllByTenant_IdOrderBySupplierNameAsc(tenantId)
                        .stream().map(StockSupplierResponse::from).toList(),
                skuRepository.findAllByTenant_IdOrderByNameAsc(tenantId)
                        .stream().map(StockSkuResponse::from).toList(),
                countRepository.findAllByTenant_IdOrderByCapturedAtDesc(
                                tenantId, PageRequest.of(0, SNAPSHOT_HISTORY_SIZE))
                        .stream().map(StockCountSubmissionResponse::from).toList(),
                receivingRepository.findAllByTenant_IdOrderByCapturedAtDesc(
                                tenantId, PageRequest.of(0, SNAPSHOT_HISTORY_SIZE))
                        .stream().map(StockReceivingResponse::from).toList()
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<StockTagResponse> listTags(
            AuthenticatedUser principal,
            String search,
            int page,
            int size
    ) {
        return PageResponse.from(
                tagRepository.searchByTenant(
                        principal.tenantId(), normaliseSearch(search), pageRequest(page, size)
                ),
                StockTagResponse::from
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
            int page,
            int size
    ) {
        return PageResponse.from(
                skuRepository.searchByTenant(
                        principal.tenantId(), normaliseSearch(search), active, pageRequest(page, size)
                ),
                StockSkuResponse::from
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<StockSkuResponse> listCopySourceSkus(
            AuthenticatedUser principal,
            UUID sourceTenantId,
            String search,
            Boolean active,
            int page,
            int size
    ) {
        if (sourceTenantId.equals(principal.tenantId())) {
            throw badRequest(
                    "COPY_SOURCE_EQUALS_TARGET",
                    "Choose a different source business."
            );
        }
        assertOwnerTenantAccess(principal, sourceTenantId);
        return PageResponse.from(
                skuRepository.searchByTenant(
                        sourceTenantId, normaliseSearch(search), active, pageRequest(page, size)
                ),
                StockSkuResponse::from
        );
    }

    @Transactional
    public CopyStockSkusResponse copySkus(
            AuthenticatedUser principal,
            CopyStockSkusRequest request
    ) {
        UUID targetTenantId = principal.tenantId();
        UUID sourceTenantId = request.sourceTenantId();
        if (sourceTenantId.equals(targetTenantId)) {
            throw badRequest(
                    "COPY_SOURCE_EQUALS_TARGET",
                    "Choose a different source business."
            );
        }

        assertOwnerTenantAccess(principal, sourceTenantId);
        UserAccount actor = assertOwnerTenantAccess(principal, targetTenantId);
        Tenant targetTenant = tenant(targetTenantId);
        Tenant sourceTenant = tenant(sourceTenantId);

        List<UUID> requestedIds = new ArrayList<>(new LinkedHashSet<>(request.skuIds()));
        List<StockSku> found = skuRepository.findAllByTenant_IdAndIdIn(
                sourceTenantId, requestedIds
        );
        if (found.size() != requestedIds.size()) {
            throw notFound(
                    "COPY_SOURCE_SKU_NOT_FOUND",
                    "One or more selected source SKUs were not found."
            );
        }
        Map<UUID, StockSku> sourceById = new LinkedHashMap<>();
        found.forEach(item -> sourceById.put(item.getId(), item));

        Map<UUID, StockTag> copiedTags = new LinkedHashMap<>();
        Map<UUID, StockSupplier> copiedSuppliers = new LinkedHashMap<>();
        List<StockSkuResponse> copiedSkus = new ArrayList<>();

        for (UUID requestedId : requestedIds) {
            StockSku source = sourceById.get(requestedId);
            StockTag targetTag1 = copiedTags.computeIfAbsent(
                    source.getTag1().getId(),
                    ignored -> tagRepository.save(
                            new StockTag(targetTenant, source.getTag1().getTag(), actor)
                    )
            );
            StockTag targetTag2 = copiedTags.computeIfAbsent(
                    source.getTag2().getId(),
                    ignored -> tagRepository.save(
                            new StockTag(targetTenant, source.getTag2().getTag(), actor)
                    )
            );

            Set<StockSupplier> targetSuppliers = new LinkedHashSet<>();
            for (StockSupplier sourceSupplier : source.getSuppliers()) {
                StockSupplier targetSupplier = copiedSuppliers.computeIfAbsent(
                        sourceSupplier.getId(),
                        ignored -> supplierRepository.save(new StockSupplier(
                                targetTenant,
                                sourceSupplier.getSupplierName(),
                                sourceSupplier.getSupplierItem(),
                                sourceSupplier.getContactPerson(),
                                sourceSupplier.getPhone(),
                                sourceSupplier.getAddress(),
                                sourceSupplier.getNotes(),
                                sourceSupplier.getUnit(),
                                sourceSupplier.getRecommendedPurchaseAmount(),
                                sourceSupplier.getRecommendedPurchaseFrequency(),
                                sourceSupplier.getPricingPerUnit(),
                                sourceSupplier.getMinimumBalanceValue(),
                                sourceSupplier.getMaximumBalanceValue(),
                                sourceSupplier.getCurrentBalanceValue(),
                                actor
                        ))
                );
                targetSuppliers.add(targetSupplier);
            }

            StockMedia sourceMedia = source.getThumbnailMedia();
            String extension = sourceMedia.getContentType().equals("image/png")
                    ? ".png"
                    : ".jpg";
            StockMedia targetMedia = mediaRepository.save(new StockMedia(
                    targetTenant,
                    UUID.randomUUID() + extension,
                    sourceMedia.getContentType(),
                    sourceMedia.getContentBytes()
            ));

            StockSku copied = skuRepository.save(new StockSku(
                    targetTenant,
                    source.getName(),
                    targetTag1,
                    targetTag2,
                    source.getUnit(),
                    source.getMinimumBalanceValue(),
                    source.getMaximumBalanceValue(),
                    BigDecimal.ZERO,
                    source.getRecoveryPercent(),
                    source.getMinimumPriceRm(),
                    source.getMaximumPriceRm(),
                    targetSuppliers,
                    targetMedia,
                    List.of(),
                    List.copyOf(source.getReceivingChecklist()),
                    source.getStockCheckFrequencyDays(),
                    source.getResetTime(),
                    source.isActive(),
                    source.isCoolingPeriod(),
                    actor
            ));

            auditRepository.save(new StockAuditEntry(
                    targetTenant,
                    "SKU",
                    "Copied SKU from another business",
                    copied.getId(),
                    copied.getName(),
                    principal,
                    "Copied from " + sourceTenant.getBusinessName()
            ).addChange("Source Business", "-", sourceTenant.getBusinessName()));
            copiedSkus.add(StockSkuResponse.from(copied));
        }

        return new CopyStockSkusResponse(
                copiedSkus.size(),
                copiedTags.size(),
                copiedSuppliers.size(),
                List.copyOf(copiedSkus)
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
        return PageResponse.from(
                countRepository.searchByTenant(
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
                ),
                StockCountSubmissionResponse::from
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
        StockTag saved = tagRepository.save(new StockTag(tenant, tag, actor));
        auditRepository.save(new StockAuditEntry(
                tenant, "Tag", "Created tag", saved.getId(), saved.getTag(), principal, "")
                .addChange("Tag", "-", saved.getTag()));
        return StockTagResponse.from(saved);
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
        tag.rename(newName, actor);
        auditRepository.save(new StockAuditEntry(
                tag.getTenant(), "Tag", "Renamed tag", tag.getId(), newName, principal, "")
                .addChange("Tag", oldName, newName));
        return StockTagResponse.from(tag);
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
        return StockSkuResponse.from(sku);
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
        String oldPhotoPath = sku.getThumbnailMedia().getStorageKey();
        if (!oldName.equalsIgnoreCase(request.name().trim())
                && skuRepository.existsByTenant_IdAndNameIgnoreCase(principal.tenantId(), request.name().trim())) {
            throw conflict("STOCK_SKU_EXISTS", "This SKU already exists.");
        }
        StockMedia thumbnail = skuThumbnail(principal, request.photoPath());
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
                .addChange("Photo", oldPhotoPath, sku.getThumbnailMedia().getStorageKey())
                .addChange("Current Balance", oldBalance, sku.getCurrentBalanceValue()));
        return StockSkuResponse.from(sku);
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
        return StockSkuResponse.from(sku);
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
        if (countRepository.existsByTenant_IdAndSku_IdAndCountCycleStartedAt(
                principal.tenantId(), sku.getId(), cycleStartedAt)) {
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
        return StockCountSubmissionResponse.from(submission);
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
        return StockCountSubmissionResponse.from(submission);
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
            responses.add(StockCountSubmissionResponse.from(submission));
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
            StockSku sku = sku(itemRequest.skuId(), principal.tenantId());
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
                        principal.tenantId(), "Pending Review", fromInclusive, toExclusive
                );
        long receivingPending = receivingRepository
                .countByTenant_IdAndReviewStatusAndCapturedAtGreaterThanEqualAndCapturedAtLessThan(
                        principal.tenantId(), "Pending Review", fromInclusive, toExclusive
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
        String status = value.trim();
        if (!Set.of("Pending Review", "Approved", "Rejected").contains(status)) {
            throw badRequest(
                    "INVALID_REVIEW_STATUS",
                    "Review status must be Pending Review, Approved or Rejected."
            );
        }
        return status;
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

    private UserAccount assertOwnerTenantAccess(
            AuthenticatedUser principal,
            UUID tenantId
    ) {
        if (!principal.isOwner()) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "OWNER_REQUIRED",
                    "Only Owner users may copy SKUs between businesses."
            );
        }
        UserAccount current = actor(principal);
        if (tenantId.equals(principal.tenantId())) {
            return current;
        }
        return userAccountRepository
                .findByTenant_IdAndIdentity_Id(tenantId, current.getIdentity().getId())
                .filter(UserAccount::isActive)
                .filter(user -> user.getRole().isActive())
                .filter(user -> user.getRole().getSystemKey() == SystemRole.OWNER)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.FORBIDDEN,
                        "TENANT_ACCESS_DENIED",
                        "This tenant is not assigned to the current Owner login."
                ));
    }

    private Tenant tenant(UUID tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> notFound("TENANT_NOT_FOUND", "Tenant not found."));
    }

    private UserAccount actor(AuthenticatedUser principal) {
        return userAccountRepository.findByIdAndTenant_Id(principal.userId(), principal.tenantId())
                .orElseThrow(() -> notFound("USER_NOT_FOUND", "User not found."));
    }

    private StockTag tag(UUID id, UUID tenantId) {
        return tagRepository.findByIdAndTenant_Id(id, tenantId)
                .orElseThrow(() -> notFound("STOCK_TAG_NOT_FOUND", "Stock tag not found."));
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
        Set<StockSupplier> result = new LinkedHashSet<>();
        for (UUID id : ids) result.add(supplier(id, tenantId));
        return result;
    }


    private void requireReceivingPhoto(UUID tenantId, String storageKey, String label) {
        String value = storageKey == null ? "" : storageKey.trim();
        if (value.isEmpty()) {
            throw badRequest(
                    "STOCK_RECEIVING_PHOTO_REQUIRED",
                    label + " photo is required. Capture and upload it first."
            );
        }
        if (mediaRepository.findByTenant_IdAndStorageKey(tenantId, value).isEmpty()) {
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
        return mediaRepository.findByTenant_IdAndStorageKey(principal.tenantId(), value)
                .orElseThrow(() -> badRequest(
                        "STOCK_THUMBNAIL_NOT_FOUND",
                        "The uploaded stock thumbnail was not found."
                ));
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
