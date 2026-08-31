package com.eastapp.backend.reports.api;

import com.eastapp.backend.activity.tracking.ActivityTracked;
import com.eastapp.backend.auth.security.AuthenticatedUser;
import com.eastapp.backend.reports.service.BusinessReportService;
import com.eastapp.backend.reports.service.ReportMediaService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reports")
public class BusinessReportsController {
    private final BusinessReportService reportService;
    private final ReportMediaService mediaService;

    public BusinessReportsController(
            BusinessReportService reportService,
            ReportMediaService mediaService
    ) {
        this.reportService = reportService;
        this.mediaService = mediaService;
    }

    @GetMapping("/dashboard")
    ReportDashboardResponse dashboard(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(defaultValue = "7") int days
    ) {
        return reportService.dashboard(principal, days);
    }

    @PostMapping(value = "/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<ReportMediaUploadResponse> uploadMedia(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestPart("file") MultipartFile file
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(mediaService.saveImage(principal, file));
    }

    @GetMapping("/media/{storageKey}")
    ResponseEntity<?> media(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable String storageKey
    ) {
        ReportMediaService.StoredReportMedia media = mediaService.loadImage(principal, storageKey);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(media.contentType()))
                .cacheControl(org.springframework.http.CacheControl.maxAge(java.time.Duration.ofHours(24)).cachePrivate())
                .body(media.resource());
    }

    @GetMapping("/sales/history")
    @PreAuthorize("hasAuthority('PERMISSION_SALES_REPORT_ACCESS')")
    List<SalesReportResponse> salesHistory(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Integer days
    ) {
        return reportService.salesHistory(principal, from, to, days);
    }

    @GetMapping("/sales")
    @PreAuthorize("hasAuthority('PERMISSION_SALES_REPORT_ACCESS')")
    SalesReportResponse sales(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return reportService.salesForDate(principal, date);
    }

    @ActivityTracked(module = "Sales", action = "saved", entity = "sales report")
    @PutMapping("/sales")
    @PreAuthorize("hasAuthority('PERMISSION_SALES_REPORT_ACCESS')")
    SalesReportResponse upsertSales(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody UpsertSalesReportRequest request
    ) {
        return reportService.upsertSales(principal, request);
    }

    @ActivityTracked(module = "Sales", action = "recorded", entity = "void bill")
    @PostMapping("/sales/void-bills")
    @PreAuthorize("hasAuthority('PERMISSION_SALES_REPORT_ACCESS')")
    ResponseEntity<VoidBillResponse> addVoidBill(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody AddVoidBillRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reportService.addVoidBill(principal, request));
    }

    @ActivityTracked(module = "Sales", action = "submitted", entity = "sales report")
    @PostMapping("/sales/submit")
    @PreAuthorize("hasAuthority('PERMISSION_SALES_REPORT_ACCESS')")
    SalesReportResponse submitSalesDirect(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody UpsertSalesReportRequest request
    ) {
        return reportService.submitSales(principal, request);
    }

    @ActivityTracked(module = "Sales", action = "submitted", entity = "sales report", targetPathVariable = "reportId")
    @PostMapping("/sales/{reportId}/submit")
    @PreAuthorize("hasAuthority('PERMISSION_SALES_REPORT_ACCESS')")
    SalesReportResponse submitSales(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID reportId
    ) {
        return reportService.submitSales(principal, reportId);
    }

    @ActivityTracked(module = "Waste", action = "submitted", entity = "waste report")
    @PostMapping("/waste")
    ResponseEntity<WasteReportResponse> createWaste(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody CreateWasteReportRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reportService.createWaste(principal, request));
    }

    @GetMapping("/waste")
    List<WasteReportResponse> wasteReports(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return reportService.wasteReports(principal, from, to);
    }

    @GetMapping("/daily-photos")
    DailyPhotoReportResponse dailyPhotos(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) UUID userId
    ) {
        return reportService.dailyPhotoReport(principal, date, userId);
    }

    @ActivityTracked(module = "Daily Photo", action = "added", entity = "daily report photo")
    @PostMapping("/daily-photos")
    DailyPhotoReportResponse addDailyPhoto(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody AddDailyPhotoRequest request
    ) {
        return reportService.addDailyPhoto(principal, request);
    }

    @ActivityTracked(module = "Daily Photo", action = "submitted", entity = "daily photo report", targetPathVariable = "reportId")
    @PostMapping("/daily-photos/{reportId}/submit")
    DailyPhotoReportResponse submitDailyPhotos(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID reportId
    ) {
        return reportService.submitDailyPhotos(principal, reportId);
    }

    @ActivityTracked(module = "Complaint", action = "created", entity = "complaint report")
    @PostMapping("/complaints")
    ResponseEntity<ComplaintReportResponse> createComplaint(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody CreateComplaintReportRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reportService.createComplaint(principal, request));
    }

    @GetMapping("/complaints")
    List<ComplaintReportResponse> complaints(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return reportService.complaintReports(principal, from, to);
    }

    @ActivityTracked(module = "Complaint", action = "updated", entity = "complaint report", targetPathVariable = "reportId")
    @PatchMapping("/complaints/{reportId}")
    @PreAuthorize("hasAuthority('PERMISSION_REPORT_OPERATIONS_ACCESS')")
    ComplaintReportResponse updateComplaint(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID reportId,
            @Valid @RequestBody UpdateComplaintRequest request
    ) {
        return reportService.updateComplaint(principal, reportId, request);
    }

    @GetMapping("/approvals")
    @PreAuthorize("hasAuthority('PERMISSION_REPORT_REVIEW')")
    List<ApprovalReportResponse> approvals(
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        return reportService.approvals(principal);
    }

    @ActivityTracked(module = "Report", action = "reviewed", entity = "business report", targetPathVariable = "reportId")
    @PostMapping("/{reportId}/review")
    @PreAuthorize("hasAuthority('PERMISSION_REPORT_REVIEW')")
    ApprovalReportResponse review(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID reportId,
            @Valid @RequestBody ReviewBusinessReportRequest request
    ) {
        return reportService.review(principal, reportId, request);
    }
}
