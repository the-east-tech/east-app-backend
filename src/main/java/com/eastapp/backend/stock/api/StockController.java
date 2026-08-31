package com.eastapp.backend.stock.api;

import com.eastapp.backend.activity.tracking.ActivityTracked;
import com.eastapp.backend.common.api.PageResponse;
import com.eastapp.backend.auth.security.AuthenticatedUser;
import com.eastapp.backend.stock.service.StockMediaService;
import com.eastapp.backend.stock.service.StockService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/stock")
public class StockController {
    private final StockService stockService;
    private final StockMediaService stockMediaService;

    public StockController(StockService stockService, StockMediaService stockMediaService) {
        this.stockService = stockService;
        this.stockMediaService = stockMediaService;
    }


    @PostMapping(value = "/media/sku-thumbnails", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('OWNER', 'HEAD')")
    ResponseEntity<StockMediaUploadResponse> uploadSkuThumbnail(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam("file") MultipartFile file
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(stockMediaService.saveSkuThumbnail(principal, file));
    }

    @GetMapping("/media/sku-thumbnails/{storageKey}")
    ResponseEntity<Resource> skuThumbnail(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable String storageKey
    ) {
        StockMediaService.StoredStockMedia media = stockMediaService.loadSkuThumbnail(principal, storageKey);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(media.contentType()))
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
                .body(media.resource());
    }

    @PostMapping(value = "/media/receiving-photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('OWNER', 'HEAD', 'MANAGER')")
    ResponseEntity<StockMediaUploadResponse> uploadReceivingPhoto(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam("file") MultipartFile file
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(stockMediaService.saveReceivingPhoto(principal, file));
    }

    @GetMapping("/media/receiving-photos/{storageKey}")
    @PreAuthorize("hasAnyRole('OWNER', 'HEAD', 'MANAGER')")
    ResponseEntity<Resource> receivingPhoto(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable String storageKey
    ) {
        StockMediaService.StoredStockMedia media = stockMediaService.loadReceivingPhoto(principal, storageKey);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(media.contentType()))
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
                .body(media.resource());
    }

    @GetMapping("/snapshot")
    StockSnapshotResponse snapshot(@AuthenticationPrincipal AuthenticatedUser principal) {
        return stockService.snapshot(principal);
    }

    @GetMapping("/tags")
    PageResponse<StockTagResponse> tags(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return stockService.listTags(principal, search, page, size);
    }

    @GetMapping("/suppliers")
    PageResponse<StockSupplierResponse> suppliers(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return stockService.listSuppliers(principal, search, page, size);
    }

    @GetMapping("/skus")
    PageResponse<StockSkuResponse> skus(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) Boolean assigned,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return stockService.listSkus(principal, search, active, assigned, page, size);
    }

    @GetMapping("/skus/copy-source")
    @PreAuthorize("hasRole('OWNER')")
    PageResponse<StockSkuResponse> copySourceSkus(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam UUID tenantId,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return stockService.listCopySourceSkus(
                principal, tenantId, search, active, page, size
        );
    }

    @ActivityTracked(module = "Stock", action = "copied", entity = "stock setup")
    @PostMapping("/skus/copy")
    @PreAuthorize("hasRole('OWNER')")
    ResponseEntity<CopyStockSkusResponse> copySkus(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody CopyStockSkusRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(stockService.copySkus(principal, request));
    }

    @GetMapping("/counts")
    PageResponse<StockCountSubmissionResponse> counts(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(defaultValue = "false") boolean mine,
            @RequestParam(required = false) String reviewStatus,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return stockService.listCounts(principal, mine, reviewStatus, from, to, page, size);
    }

    @GetMapping("/receivings")
    @PreAuthorize("hasAnyRole('OWNER', 'HEAD', 'MANAGER')")
    PageResponse<StockReceivingResponse> receivings(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(required = false) String reviewStatus,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return stockService.listReceivings(principal, reviewStatus, from, to, page, size);
    }

    @ActivityTracked(module = "Stock", action = "created", entity = "stock tag")
    @PostMapping("/tags")
    @PreAuthorize("hasAnyRole('OWNER', 'HEAD')")
    ResponseEntity<StockTagResponse> createTag(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody CreateStockTagRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(stockService.createTag(principal, request));
    }

    @ActivityTracked(module = "Stock", action = "updated", entity = "stock tag", targetPathVariable = "tagId")
    @PatchMapping("/tags/{tagId}")
    @PreAuthorize("hasAnyRole('OWNER', 'HEAD')")
    StockTagResponse updateTag(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID tagId,
            @Valid @RequestBody UpdateStockTagRequest request
    ) {
        return stockService.updateTag(principal, tagId, request);
    }

    @ActivityTracked(module = "Stock", action = "deleted", entity = "stock tag", targetPathVariable = "tagId")
    @DeleteMapping("/tags/{tagId}")
    @PreAuthorize("hasAnyRole('OWNER', 'HEAD')")
    ResponseEntity<Void> deleteTag(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID tagId
    ) {
        stockService.deleteTag(principal, tagId);
        return ResponseEntity.noContent().build();
    }

    @ActivityTracked(module = "Stock", action = "created", entity = "supplier")
    @PostMapping("/suppliers")
    @PreAuthorize("hasAnyRole('OWNER', 'HEAD')")
    ResponseEntity<StockSupplierResponse> createSupplier(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody CreateStockSupplierRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(stockService.createSupplier(principal, request));
    }

    @ActivityTracked(module = "Stock", action = "updated", entity = "supplier", targetPathVariable = "supplierId")
    @PatchMapping("/suppliers/{supplierId}")
    @PreAuthorize("hasAnyRole('OWNER', 'HEAD')")
    StockSupplierResponse updateSupplier(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID supplierId,
            @Valid @RequestBody CreateStockSupplierRequest request
    ) {
        return stockService.updateSupplier(principal, supplierId, request);
    }

    @ActivityTracked(module = "Stock", action = "deleted", entity = "supplier", targetPathVariable = "supplierId")
    @DeleteMapping("/suppliers/{supplierId}")
    @PreAuthorize("hasAnyRole('OWNER', 'HEAD')")
    ResponseEntity<Void> deleteSupplier(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID supplierId
    ) {
        stockService.deleteSupplier(principal, supplierId);
        return ResponseEntity.noContent().build();
    }

    @ActivityTracked(module = "Stock", action = "updated", entity = "supplier balance", targetPathVariable = "supplierId")
    @PatchMapping("/suppliers/{supplierId}/balance")
    @PreAuthorize("hasAnyRole('OWNER', 'HEAD', 'MANAGER')")
    StockSupplierResponse updateSupplierBalance(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID supplierId,
            @Valid @RequestBody UpdateStockBalanceRequest request
    ) {
        return stockService.updateSupplierBalance(principal, supplierId, request);
    }

    @ActivityTracked(module = "Stock", action = "created", entity = "stock item")
    @PostMapping("/skus")
    @PreAuthorize("hasAnyRole('OWNER', 'HEAD')")
    ResponseEntity<StockSkuResponse> createSku(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody UpsertStockSkuRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(stockService.createSku(principal, request));
    }

    @ActivityTracked(module = "Stock", action = "updated", entity = "stock item", targetPathVariable = "skuId")
    @PatchMapping("/skus/{skuId}")
    @PreAuthorize("hasAnyRole('OWNER', 'HEAD')")
    StockSkuResponse updateSku(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID skuId,
            @Valid @RequestBody UpsertStockSkuRequest request
    ) {
        return stockService.updateSku(principal, skuId, request);
    }

    @ActivityTracked(module = "Stock", action = "updated", entity = "stock balance", targetPathVariable = "skuId")
    @PatchMapping("/skus/{skuId}/balance")
    @PreAuthorize("hasAnyRole('OWNER', 'HEAD', 'MANAGER')")
    StockSkuResponse updateSkuBalance(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID skuId,
            @Valid @RequestBody UpdateStockBalanceRequest request
    ) {
        return stockService.updateSkuBalance(principal, skuId, request);
    }

    @ActivityTracked(module = "Stock", action = "submitted", entity = "stock count")
    @PostMapping("/counts")
    ResponseEntity<StockCountSubmissionResponse> createCount(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody CreateStockCountRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(stockService.createCount(principal, request));
    }

    @ActivityTracked(module = "Stock", action = "reviewed", entity = "stock count", targetPathVariable = "submissionId")
    @PatchMapping("/counts/{submissionId}/review")
    @PreAuthorize("hasAnyRole('OWNER', 'HEAD', 'MANAGER')")
    StockCountSubmissionResponse reviewCount(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID submissionId,
            @Valid @RequestBody ReviewStockRecordRequest request
    ) {
        return stockService.reviewCount(principal, submissionId, request);
    }

    @ActivityTracked(module = "Stock", action = "reviewed", entity = "stock counts")
    @PatchMapping("/counts/bulk-review")
    @PreAuthorize("hasAnyRole('OWNER', 'HEAD', 'MANAGER')")
    BulkReviewStockCountsResponse bulkReviewCounts(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody BulkReviewStockCountsRequest request
    ) {
        return stockService.bulkReviewCounts(principal, request);
    }

    @ActivityTracked(module = "Stock", action = "submitted", entity = "stock receiving")
    @PostMapping("/receivings")
    @PreAuthorize("hasAnyRole('OWNER', 'HEAD', 'MANAGER')")
    ResponseEntity<StockReceivingResponse> createReceiving(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody CreateStockReceivingRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(stockService.createReceiving(principal, request));
    }

    @ActivityTracked(module = "Stock", action = "reviewed", entity = "stock receiving", targetPathVariable = "receivingId")
    @PatchMapping("/receivings/{receivingId}/review")
    @PreAuthorize("hasAnyRole('OWNER', 'HEAD', 'MANAGER')")
    StockReceivingResponse reviewReceiving(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID receivingId,
            @Valid @RequestBody ReviewStockRecordRequest request
    ) {
        return stockService.reviewReceiving(principal, receivingId, request);
    }

    @GetMapping("/reviews/today-summary")
    @PreAuthorize("hasAnyRole('OWNER', 'HEAD', 'MANAGER')")
    StockReviewSummaryResponse todayReviewSummary(
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        return stockService.todayReviewSummary(principal);
    }

    @GetMapping("/audit")
    PageResponse<StockAuditEntryResponse> audit(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "false") boolean mine,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return stockService.audit(principal, from, to, mine, page, size);
    }
}
