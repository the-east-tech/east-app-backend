package com.eastapp.backend.reports.service;

import com.eastapp.backend.auth.security.AuthenticatedUser;
import com.eastapp.backend.common.error.ApiException;
import com.eastapp.backend.people.SystemRole;
import com.eastapp.backend.people.UserAccount;
import com.eastapp.backend.people.UserAccountRepository;
import com.eastapp.backend.reports.BusinessReport;
import com.eastapp.backend.reports.BusinessReportRepository;
import com.eastapp.backend.reports.BusinessReportType;
import com.eastapp.backend.reports.ComplaintReportDetail;
import com.eastapp.backend.reports.ComplaintReportDetailRepository;
import com.eastapp.backend.reports.ComplaintStatus;
import com.eastapp.backend.reports.DailyReportPhoto;
import com.eastapp.backend.reports.DailyReportPhotoRepository;
import com.eastapp.backend.reports.ReportMedia;
import com.eastapp.backend.reports.ReportMediaRepository;
import com.eastapp.backend.reports.ReportWorkflowStatus;
import com.eastapp.backend.reports.SalesReportDetail;
import com.eastapp.backend.reports.SalesReportDetailRepository;
import com.eastapp.backend.reports.SalesVoidBill;
import com.eastapp.backend.reports.SalesVoidBillRepository;
import com.eastapp.backend.reports.WasteReportDetail;
import com.eastapp.backend.reports.WasteReportDetailRepository;
import com.eastapp.backend.reports.api.AddDailyPhotoRequest;
import com.eastapp.backend.reports.api.AddVoidBillRequest;
import com.eastapp.backend.reports.api.ApprovalReportResponse;
import com.eastapp.backend.reports.api.ComplaintOverviewResponse;
import com.eastapp.backend.reports.api.ComplaintReportResponse;
import com.eastapp.backend.reports.api.CreateComplaintReportRequest;
import com.eastapp.backend.reports.api.CreateWasteReportRequest;
import com.eastapp.backend.reports.api.DailyPhotoItemResponse;
import com.eastapp.backend.reports.api.DailyPhotoOverviewResponse;
import com.eastapp.backend.reports.api.DailyPhotoReportResponse;
import com.eastapp.backend.reports.api.InventoryIntelligenceResponse;
import com.eastapp.backend.reports.api.InventoryRiskResponse;
import com.eastapp.backend.reports.api.ReportDashboardResponse;
import com.eastapp.backend.reports.api.ReportTrendPointResponse;
import com.eastapp.backend.reports.api.ReviewBusinessReportRequest;
import com.eastapp.backend.reports.api.SalesOverviewResponse;
import com.eastapp.backend.reports.api.SalesReportResponse;
import com.eastapp.backend.reports.api.UpdateComplaintRequest;
import com.eastapp.backend.reports.api.UpsertSalesReportRequest;
import com.eastapp.backend.reports.api.VoidBillResponse;
import com.eastapp.backend.reports.api.WasteOverviewResponse;
import com.eastapp.backend.reports.api.WasteReportResponse;
import com.eastapp.backend.reports.config.ReportProperties;
import com.eastapp.backend.stock.StockSku;
import com.eastapp.backend.stock.StockSkuRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class BusinessReportService {
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal TWO = new BigDecimal("2");
    private static final Set<ReportWorkflowStatus> ANALYTICS_STATUSES = Set.of(
            ReportWorkflowStatus.SUBMITTED,
            ReportWorkflowStatus.APPROVED
    );

    private final BusinessReportRepository reportRepository;
    private final SalesReportDetailRepository salesRepository;
    private final SalesVoidBillRepository voidBillRepository;
    private final WasteReportDetailRepository wasteRepository;
    private final DailyReportPhotoRepository dailyPhotoRepository;
    private final ComplaintReportDetailRepository complaintRepository;
    private final ReportMediaRepository mediaRepository;
    private final ReportMediaService reportMediaService;
    private final UserAccountRepository userRepository;
    private final StockSkuRepository skuRepository;
    private final ReportProperties properties;

    public BusinessReportService(
            BusinessReportRepository reportRepository,
            SalesReportDetailRepository salesRepository,
            SalesVoidBillRepository voidBillRepository,
            WasteReportDetailRepository wasteRepository,
            DailyReportPhotoRepository dailyPhotoRepository,
            ComplaintReportDetailRepository complaintRepository,
            ReportMediaRepository mediaRepository,
            ReportMediaService reportMediaService,
            UserAccountRepository userRepository,
            StockSkuRepository skuRepository,
            ReportProperties properties
    ) {
        this.reportRepository = reportRepository;
        this.salesRepository = salesRepository;
        this.voidBillRepository = voidBillRepository;
        this.wasteRepository = wasteRepository;
        this.dailyPhotoRepository = dailyPhotoRepository;
        this.complaintRepository = complaintRepository;
        this.mediaRepository = mediaRepository;
        this.reportMediaService = reportMediaService;
        this.userRepository = userRepository;
        this.skuRepository = skuRepository;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public ReportDashboardResponse dashboard(AuthenticatedUser principal, int requestedDays) {
        int days = Math.max(1, Math.min(requestedDays, 31));
        LocalDate today = today();
        LocalDate from = today.minusDays(days - 1L);
        boolean managementView = isManagement(principal.systemRole());

        DailyPhotoOverviewResponse dailyOverview = dailyPhotoOverview(principal, today);
        if (!managementView) {
            return new ReportDashboardResponse(
                    today,
                    days,
                    false,
                    null,
                    null,
                    null,
                    dailyOverview,
                    null,
                    0,
                    List.of()
            );
        }

        List<BusinessReport> periodSalesReports = reportRepository
                .findAllByTenantIdAndReportTypeAndReportDateBetweenOrderByReportDateAscCreatedAtAsc(
                        principal.tenantId(), BusinessReportType.SALES, from, today
                ).stream()
                .filter(this::includedForAnalytics)
                .toList();
        Map<UUID, SalesReportDetail> periodSales = salesDetails(periodSalesReports);
        Map<UUID, BigDecimal> periodVoids = voidTotals(periodSalesReports);

        LocalDate previousTo = from.minusDays(1);
        LocalDate previousFrom = previousTo.minusDays(days - 1L);
        List<BusinessReport> previousSalesReports = reportRepository
                .findAllByTenantIdAndReportTypeAndReportDateBetweenOrderByReportDateAscCreatedAtAsc(
                        principal.tenantId(), BusinessReportType.SALES, previousFrom, previousTo
                ).stream()
                .filter(this::includedForAnalytics)
                .toList();
        Map<UUID, SalesReportDetail> previousSales = salesDetails(previousSalesReports);
        Map<UUID, BigDecimal> previousVoids = voidTotals(previousSalesReports);

        SalesOverviewResponse salesOverview = salesOverview(
                today,
                periodSalesReports,
                periodSales,
                periodVoids,
                previousSalesReports,
                previousSales,
                previousVoids
        );
        InventoryIntelligenceResponse inventory = inventoryIntelligence(principal.tenantId());

        List<BusinessReport> wasteReports = reportRepository
                .findAllByTenantIdAndReportTypeAndReportDateBetweenOrderByReportDateAscCreatedAtAsc(
                        principal.tenantId(), BusinessReportType.WASTE, from, today
                ).stream()
                .filter(this::includedForAnalytics)
                .toList();
        Map<UUID, WasteReportDetail> wasteDetails = wasteDetails(wasteReports);
        WasteOverviewResponse wasteOverview = wasteOverview(
                today,
                wasteReports,
                wasteDetails,
                netSalesTotal(periodSalesReports, periodSales, periodVoids)
        );

        List<BusinessReport> complaintReports = reportRepository
                .findAllByTenantIdAndReportTypeAndReportDateBetweenOrderByReportDateAscCreatedAtAsc(
                        principal.tenantId(), BusinessReportType.COMPLAINT, from, today
                );
        Map<UUID, ComplaintReportDetail> complaintDetails = complaintDetails(complaintReports);
        ComplaintOverviewResponse complaints = complaintOverview(complaintDetails.values());

        int pendingApprovals = reportRepository
                .findAllByTenantIdAndWorkflowStatusOrderBySubmittedAtAsc(
                        principal.tenantId(), ReportWorkflowStatus.SUBMITTED
                ).size();

        List<ReportTrendPointResponse> trend = buildTrend(
                from,
                today,
                periodSalesReports,
                periodSales,
                periodVoids,
                wasteReports,
                wasteDetails
        );

        return new ReportDashboardResponse(
                today,
                days,
                true,
                salesOverview,
                inventory,
                wasteOverview,
                dailyOverview,
                complaints,
                pendingApprovals,
                trend
        );
    }

    @Transactional(readOnly = true)
    public SalesReportResponse salesForDate(AuthenticatedUser principal, LocalDate date) {
        requireManagement(principal);
        LocalDate reportDate = date == null ? today() : date;
        validateViewDate(reportDate);
        Optional<BusinessReport> optional = reportRepository.findByTenantIdAndReportTypeAndReportDate(
                principal.tenantId(), BusinessReportType.SALES, reportDate
        );
        if (optional.isEmpty()) {
            return emptySales(reportDate);
        }
        return toSalesResponse(optional.get(), userNames(principal.tenantId()));
    }

    @Transactional
    public SalesReportResponse upsertSales(
            AuthenticatedUser principal,
            UpsertSalesReportRequest request
    ) {
        requireManagement(principal);
        validateEditableDate(principal, request.reportDate());
        BusinessReport report = reportRepository.findByTenantIdAndReportTypeAndReportDate(
                principal.tenantId(), BusinessReportType.SALES, request.reportDate()
        ).orElseGet(() -> reportRepository.saveAndFlush(new BusinessReport(
                principal.tenantId(),
                BusinessReportType.SALES,
                request.reportDate(),
                principal.userId()
        )));
        try {
            report.reopenForEditing();
            report.assignSubmitter(principal.userId());
        } catch (IllegalStateException exception) {
            throw locked(exception.getMessage());
        }
        reportRepository.save(report);

        SalesReportDetail detail = salesRepository.findByReportIdAndTenantId(
                report.getId(), principal.tenantId()
        ).orElseGet(() -> new SalesReportDetail(
                report.getId(),
                principal.tenantId(),
                request.cashTotalRm(),
                request.cashReceivedBy(),
                request.foodDeliverySalesRm(),
                request.ewalletTotalRm(),
                request.staffOnDuty()
        ));
        detail.update(
                request.cashTotalRm(),
                request.cashReceivedBy(),
                request.foodDeliverySalesRm(),
                request.ewalletTotalRm(),
                request.staffOnDuty()
        );
        salesRepository.save(detail);
        return toSalesResponse(report, userNames(principal.tenantId()));
    }

    @Transactional
    public VoidBillResponse addVoidBill(
            AuthenticatedUser principal,
            AddVoidBillRequest request
    ) {
        validateEditableDate(principal, request.reportDate());
        ReportMedia media = reportMediaService.requireOwnedMedia(principal, request.photoStorageKey());
        BusinessReport report = reportRepository.findByTenantIdAndReportTypeAndReportDate(
                principal.tenantId(), BusinessReportType.SALES, request.reportDate()
        ).orElseGet(() -> reportRepository.saveAndFlush(new BusinessReport(
                principal.tenantId(),
                BusinessReportType.SALES,
                request.reportDate(),
                principal.userId()
        )));
        if (report.getWorkflowStatus() == ReportWorkflowStatus.REJECTED) {
            report.reopenForEditing();
            reportRepository.save(report);
        }
        if (report.getWorkflowStatus() != ReportWorkflowStatus.DRAFT) {
            throw locked("The submitted sales report is locked. Ask management to reject it before adding another void bill.");
        }
        if (voidBillRepository.existsByTenantIdAndSalesReportIdAndBillNumberIgnoreCase(
                principal.tenantId(), report.getId(), request.billNumber().trim()
        )) {
            throw new ApiException(HttpStatus.CONFLICT, "VOID_BILL_DUPLICATE", "This bill number already exists in today's sales report.");
        }
        SalesVoidBill saved = voidBillRepository.saveAndFlush(new SalesVoidBill(
                principal.tenantId(),
                report.getId(),
                media.getId(),
                request.billNumber(),
                request.reason(),
                request.amountRm(),
                principal.userId()
        ));
        return toVoidBillResponse(saved, media, userNames(principal.tenantId()));
    }

    @Transactional
    public SalesReportResponse submitSales(
            AuthenticatedUser principal,
            UpsertSalesReportRequest request
    ) {
        SalesReportResponse saved = upsertSales(principal, request);
        if (saved.id() == null) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "SALES_REPORT_NOT_CREATED", "The sales report could not be created.");
        }
        return submitSales(principal, saved.id());
    }

    @Transactional
    public SalesReportResponse submitSales(AuthenticatedUser principal, UUID reportId) {
        requireManagement(principal);
        BusinessReport report = requireReport(principal, reportId, BusinessReportType.SALES);
        if (salesRepository.findByReportIdAndTenantId(reportId, principal.tenantId()).isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SALES_DETAILS_REQUIRED", "Enter the daily sales totals before submission.");
        }
        try {
            report.submit();
        } catch (IllegalStateException exception) {
            throw locked(exception.getMessage());
        }
        reportRepository.save(report);
        return toSalesResponse(report, userNames(principal.tenantId()));
    }

    @Transactional
    public WasteReportResponse createWaste(
            AuthenticatedUser principal,
            CreateWasteReportRequest request
    ) {
        validateEditableDate(principal, request.reportDate());
        ReportMedia media = reportMediaService.requireOwnedMedia(principal, request.photoStorageKey());
        UUID skuId = request.skuId();
        String itemName = request.itemName().trim();
        String unit = request.unit().trim();
        BigDecimal unitCost = request.estimatedUnitCostRm();
        if (skuId != null) {
            StockSku sku = skuRepository.findByIdAndTenant_Id(skuId, principal.tenantId())
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "SKU_NOT_FOUND", "The selected SKU was not found."));
            itemName = sku.getName();
            unit = sku.getUnit();
            if (unitCost.signum() == 0) {
                unitCost = midpoint(sku.getMinimumPriceRm(), sku.getMaximumPriceRm());
            }
        }

        BusinessReport report = reportRepository.saveAndFlush(new BusinessReport(
                principal.tenantId(),
                BusinessReportType.WASTE,
                request.reportDate(),
                principal.userId()
        ));
        WasteReportDetail detail = wasteRepository.saveAndFlush(new WasteReportDetail(
                report.getId(),
                principal.tenantId(),
                skuId,
                itemName,
                request.quantity(),
                unit,
                unitCost,
                request.reason(),
                media.getId()
        ));
        report.submit();
        reportRepository.save(report);
        return toWasteResponse(report, detail, media, userNames(principal.tenantId()));
    }

    @Transactional(readOnly = true)
    public List<WasteReportResponse> wasteReports(
            AuthenticatedUser principal,
            LocalDate from,
            LocalDate to
    ) {
        DateRange range = dateRange(from, to, 31);
        Map<UUID, String> names = userNames(principal.tenantId());
        List<BusinessReport> reports = reportRepository
                .findAllByTenantIdAndReportTypeAndReportDateBetweenOrderByReportDateAscCreatedAtAsc(
                        principal.tenantId(), BusinessReportType.WASTE, range.from(), range.to()
                ).stream()
                .filter(report -> isManagement(principal.systemRole())
                        || report.getSubmittedByUserId().equals(principal.userId()))
                .sorted(Comparator.comparing(BusinessReport::getCreatedAt).reversed())
                .toList();
        Map<UUID, WasteReportDetail> details = wasteDetails(reports);
        Map<UUID, ReportMedia> media = mediaById(
                principal.tenantId(),
                details.values().stream().map(WasteReportDetail::getPhotoMediaId).toList()
        );
        List<WasteReportResponse> result = new ArrayList<>();
        for (BusinessReport report : reports) {
            WasteReportDetail detail = details.get(report.getId());
            if (detail == null) continue;
            ReportMedia photo = media.get(detail.getPhotoMediaId());
            result.add(toWasteResponse(report, detail, photo, names));
        }
        return List.copyOf(result);
    }

    @Transactional(readOnly = true)
    public DailyPhotoReportResponse dailyPhotoReport(
            AuthenticatedUser principal,
            LocalDate date,
            UUID requestedUserId
    ) {
        LocalDate reportDate = date == null ? today() : date;
        validateViewDate(reportDate);
        UUID userId = requestedUserId == null ? principal.userId() : requestedUserId;
        if (!userId.equals(principal.userId()) && !isManagement(principal.systemRole())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "REPORT_ACCESS_DENIED", "U can only view ur own daily photos.");
        }
        UserAccount user = userRepository.findByIdAndTenant_Id(userId, principal.tenantId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "The selected user was not found."));
        Optional<BusinessReport> report = reportRepository
                .findByTenantIdAndReportTypeAndReportDateAndSubmittedByUserId(
                        principal.tenantId(), BusinessReportType.DAILY_PHOTO, reportDate, userId
                );
        return report.map(value -> toDailyPhotoResponse(value, user.getFullName()))
                .orElseGet(() -> emptyDailyPhoto(reportDate, user));
    }

    @Transactional
    public DailyPhotoReportResponse addDailyPhoto(
            AuthenticatedUser principal,
            AddDailyPhotoRequest request
    ) {
        validateEditableDate(principal, request.reportDate());
        ReportMedia media = reportMediaService.requireOwnedMedia(principal, request.photoStorageKey());
        BusinessReport report = reportRepository
                .findByTenantIdAndReportTypeAndReportDateAndSubmittedByUserId(
                        principal.tenantId(), BusinessReportType.DAILY_PHOTO, request.reportDate(), principal.userId()
                )
                .orElseGet(() -> reportRepository.saveAndFlush(new BusinessReport(
                        principal.tenantId(),
                        BusinessReportType.DAILY_PHOTO,
                        request.reportDate(),
                        principal.userId()
                )));
        if (report.getWorkflowStatus() == ReportWorkflowStatus.REJECTED) {
            report.reopenForEditing();
            reportRepository.save(report);
        }
        if (report.getWorkflowStatus() != ReportWorkflowStatus.DRAFT) {
            throw locked("This daily photo batch has already been submitted.");
        }
        dailyPhotoRepository.saveAndFlush(new DailyReportPhoto(
                principal.tenantId(), report.getId(), media.getId(), principal.userId()
        ));
        return toDailyPhotoResponse(report, principal.fullName());
    }

    @Transactional
    public DailyPhotoReportResponse submitDailyPhotos(
            AuthenticatedUser principal,
            UUID reportId
    ) {
        BusinessReport report = requireReport(principal, reportId, BusinessReportType.DAILY_PHOTO);
        if (!report.getSubmittedByUserId().equals(principal.userId()) && !isSeniorManagement(principal.systemRole())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "REPORT_ACCESS_DENIED", "U can only submit ur own daily photos.");
        }
        long photoCount = dailyPhotoRepository.countByTenantIdAndReportId(principal.tenantId(), reportId);
        if (photoCount < properties.getDailyPhotoMinimum()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "DAILY_PHOTOS_INCOMPLETE",
                    "Take at least " + properties.getDailyPhotoMinimum() + " photos before submission."
            );
        }
        try {
            report.submit();
        } catch (IllegalStateException exception) {
            throw locked(exception.getMessage());
        }
        reportRepository.save(report);
        return toDailyPhotoResponse(report, userName(report.getSubmittedByUserId(), userNames(principal.tenantId())));
    }

    @Transactional
    public ComplaintReportResponse createComplaint(
            AuthenticatedUser principal,
            CreateComplaintReportRequest request
    ) {
        validateEditableDate(principal, request.reportDate());
        ReportMedia media = reportMediaService.requireOwnedMedia(principal, request.photoStorageKey());
        BusinessReport report = reportRepository.saveAndFlush(new BusinessReport(
                principal.tenantId(),
                BusinessReportType.COMPLAINT,
                request.reportDate(),
                principal.userId()
        ));
        ComplaintReportDetail detail = complaintRepository.saveAndFlush(new ComplaintReportDetail(
                report.getId(),
                principal.tenantId(),
                media.getId(),
                request.customerGender(),
                request.estimatedAge(),
                request.complaintInfo(),
                request.phoneE164(),
                request.actionTaken(),
                request.compensationAmountRm(),
                request.status()
        ));
        report.markCompleteWithoutApproval();
        reportRepository.save(report);
        return toComplaintResponse(report, detail, media, userNames(principal.tenantId()));
    }

    @Transactional(readOnly = true)
    public List<ComplaintReportResponse> complaintReports(
            AuthenticatedUser principal,
            LocalDate from,
            LocalDate to
    ) {
        DateRange range = dateRange(from, to, 90);
        Map<UUID, String> names = userNames(principal.tenantId());
        List<BusinessReport> reports = reportRepository
                .findAllByTenantIdAndReportTypeAndReportDateBetweenOrderByReportDateAscCreatedAtAsc(
                        principal.tenantId(), BusinessReportType.COMPLAINT, range.from(), range.to()
                ).stream()
                .filter(report -> isManagement(principal.systemRole())
                        || report.getSubmittedByUserId().equals(principal.userId()))
                .sorted(Comparator.comparing(BusinessReport::getCreatedAt).reversed())
                .toList();
        Map<UUID, ComplaintReportDetail> details = complaintDetails(reports);
        Map<UUID, ReportMedia> media = mediaById(
                principal.tenantId(),
                details.values().stream().map(ComplaintReportDetail::getPhotoMediaId).toList()
        );
        List<ComplaintReportResponse> result = new ArrayList<>();
        for (BusinessReport report : reports) {
            ComplaintReportDetail detail = details.get(report.getId());
            if (detail == null) continue;
            result.add(toComplaintResponse(report, detail, media.get(detail.getPhotoMediaId()), names));
        }
        return List.copyOf(result);
    }

    @Transactional
    public ComplaintReportResponse updateComplaint(
            AuthenticatedUser principal,
            UUID reportId,
            UpdateComplaintRequest request
    ) {
        requireManagement(principal);
        BusinessReport report = requireReport(principal, reportId, BusinessReportType.COMPLAINT);
        ComplaintReportDetail detail = complaintRepository.findByReportIdAndTenantId(reportId, principal.tenantId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "COMPLAINT_NOT_FOUND", "Complaint report was not found."));
        detail.updateResolution(request.actionTaken(), request.compensationAmountRm(), request.status());
        complaintRepository.save(detail);
        ReportMedia media = mediaRepository.findByIdAndTenantId(detail.getPhotoMediaId(), principal.tenantId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "REPORT_MEDIA_NOT_FOUND", "Complaint photo was not found."));
        return toComplaintResponse(report, detail, media, userNames(principal.tenantId()));
    }

    @Transactional(readOnly = true)
    public List<ApprovalReportResponse> approvals(AuthenticatedUser principal) {
        requireReviewer(principal);
        List<BusinessReport> reports = reportRepository
                .findAllByTenantIdAndWorkflowStatusOrderBySubmittedAtAsc(
                        principal.tenantId(), ReportWorkflowStatus.SUBMITTED
                );
        Map<UUID, String> names = userNames(principal.tenantId());
        Map<UUID, SalesReportDetail> sales = salesDetails(reports.stream()
                .filter(report -> report.getReportType() == BusinessReportType.SALES).toList());
        Map<UUID, BigDecimal> voidTotals = voidTotals(reports.stream()
                .filter(report -> report.getReportType() == BusinessReportType.SALES).toList());
        Map<UUID, WasteReportDetail> waste = wasteDetails(reports.stream()
                .filter(report -> report.getReportType() == BusinessReportType.WASTE).toList());

        List<ApprovalReportResponse> result = new ArrayList<>();
        for (BusinessReport report : reports) {
            String summary;
            BigDecimal amount = BigDecimal.ZERO;
            int evidenceCount = 0;
            if (report.getReportType() == BusinessReportType.SALES) {
                SalesReportDetail detail = sales.get(report.getId());
                BigDecimal voidAmount = voidTotals.getOrDefault(report.getId(), BigDecimal.ZERO);
                amount = detail == null ? BigDecimal.ZERO : detail.grossSalesRm();
                evidenceCount = voidBillRepository
                        .findAllByTenantIdAndSalesReportIdOrderByCreatedAtAsc(principal.tenantId(), report.getId())
                        .size();
                summary = "Total sales RM " + money(amount) + " · " + evidenceCount + " void bill(s)";
            } else if (report.getReportType() == BusinessReportType.WASTE) {
                WasteReportDetail detail = waste.get(report.getId());
                amount = detail == null ? BigDecimal.ZERO : detail.estimatedLossRm();
                evidenceCount = detail == null ? 0 : 1;
                summary = detail == null ? "Waste report" : detail.getItemName() + " · RM " + money(amount);
            } else if (report.getReportType() == BusinessReportType.DAILY_PHOTO) {
                evidenceCount = (int) dailyPhotoRepository.countByTenantIdAndReportId(principal.tenantId(), report.getId());
                summary = evidenceCount + " daily photos";
            } else {
                continue;
            }
            result.add(new ApprovalReportResponse(
                    report.getId(),
                    report.getReportType(),
                    report.getReportDate(),
                    report.getSubmittedByUserId(),
                    userName(report.getSubmittedByUserId(), names),
                    report.getSubmittedAt(),
                    summary,
                    amount.setScale(2, RoundingMode.HALF_UP),
                    evidenceCount
            ));
        }
        return List.copyOf(result);
    }

    @Transactional
    public ApprovalReportResponse review(
            AuthenticatedUser principal,
            UUID reportId,
            ReviewBusinessReportRequest request
    ) {
        requireReviewer(principal);
        BusinessReport report = reportRepository.findByIdAndTenantIdForUpdate(reportId, principal.tenantId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "REPORT_NOT_FOUND", "Report was not found."));
        if (report.getReportType() == BusinessReportType.COMPLAINT) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REPORT_REVIEW_NOT_REQUIRED", "Complaints use Open and Resolved status instead of approval.");
        }
        if (report.getSubmittedByUserId().equals(principal.userId()) && !isSeniorManagement(principal.systemRole())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "SELF_REVIEW_NOT_ALLOWED", "Managers cannot approve their own report.");
        }
        try {
            if (request.status() == ReportWorkflowStatus.APPROVED) {
                report.approve(principal.userId(), request.note());
            } else if (request.status() == ReportWorkflowStatus.REJECTED) {
                report.reject(principal.userId(), request.note());
            } else {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REVIEW_STATUS", "Review status must be APPROVED or REJECTED.");
            }
        } catch (IllegalStateException exception) {
            throw locked(exception.getMessage());
        } catch (IllegalArgumentException exception) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_REVIEW_NOTE",
                    exception.getMessage()
            );
        }
        reportRepository.saveAndFlush(report);
        return approvalsForSingle(report, userNames(principal.tenantId()));
    }

    private SalesOverviewResponse salesOverview(
            LocalDate today,
            List<BusinessReport> reports,
            Map<UUID, SalesReportDetail> details,
            Map<UUID, BigDecimal> voidTotals,
            List<BusinessReport> previousReports,
            Map<UUID, SalesReportDetail> previousDetails,
            Map<UUID, BigDecimal> previousVoids
    ) {
        BusinessReport todayReport = reports.stream()
                .filter(report -> report.getReportDate().equals(today))
                .findFirst()
                .orElse(null);
        BigDecimal gross = reports.stream()
                .map(report -> details.get(report.getId()))
                .filter(Objects::nonNull)
                .map(SalesReportDetail::grossSalesRm)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal voidAmount = reports.stream()
                .map(report -> voidTotals.getOrDefault(report.getId(), BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal net = gross;
        int staffCount = reports.stream()
                .map(report -> details.get(report.getId()))
                .filter(Objects::nonNull)
                .mapToInt(SalesReportDetail::getStaffCount)
                .sum();
        BigDecimal perStaff = staffCount == 0
                ? BigDecimal.ZERO
                : net.divide(BigDecimal.valueOf(staffCount), 2, RoundingMode.HALF_UP);
        BigDecimal voidRate = percentage(voidAmount, gross.add(voidAmount));

        BigDecimal currentPeriod = netSalesTotal(reports, details, voidTotals);
        BigDecimal previousPeriod = netSalesTotal(previousReports, previousDetails, previousVoids);
        BigDecimal change = previousPeriod.signum() == 0
                ? (currentPeriod.signum() == 0 ? BigDecimal.ZERO : HUNDRED)
                : currentPeriod.subtract(previousPeriod)
                        .divide(previousPeriod, 4, RoundingMode.HALF_UP)
                        .multiply(HUNDRED)
                        .setScale(1, RoundingMode.HALF_UP);

        return new SalesOverviewResponse(
                moneyValue(gross),
                moneyValue(net),
                moneyValue(voidAmount),
                moneyValue(perStaff),
                percentValue(voidRate),
                change,
                staffCount,
                todayReport != null
        );
    }

    private InventoryIntelligenceResponse inventoryIntelligence(UUID tenantId) {
        List<StockSku> skus = skuRepository.findAllByTenant_IdOrderByNameAsc(tenantId).stream()
                .filter(StockSku::isActive)
                .toList();
        int healthy = 0;
        int low = 0;
        int out = 0;
        int over = 0;
        BigDecimal stockValue = BigDecimal.ZERO;
        BigDecimal reorderInvestment = BigDecimal.ZERO;
        BigDecimal overstockCapital = BigDecimal.ZERO;
        List<InventoryRiskResponse> risks = new ArrayList<>();

        for (StockSku sku : skus) {
            BigDecimal current = sku.getCurrentBalanceValue();
            BigDecimal min = sku.getMinimumBalanceValue();
            BigDecimal max = sku.getMaximumBalanceValue();
            BigDecimal price = midpoint(sku.getMinimumPriceRm(), sku.getMaximumPriceRm());
            stockValue = stockValue.add(current.multiply(price));

            String severity = null;
            BigDecimal valueAtRisk = BigDecimal.ZERO;
            String insight = "Balance is within the configured operating range.";
            if (current.signum() == 0) {
                out++;
                severity = "CRITICAL";
                BigDecimal target = recoveryTarget(sku);
                valueAtRisk = target.multiply(price);
                reorderInvestment = reorderInvestment.add(valueAtRisk);
                insight = "Out of stock. Replenish to the recovery target to restore availability.";
            } else if (current.compareTo(min) < 0) {
                low++;
                severity = "HIGH";
                BigDecimal target = recoveryTarget(sku);
                BigDecimal shortage = target.subtract(current).max(BigDecimal.ZERO);
                valueAtRisk = shortage.multiply(price);
                reorderInvestment = reorderInvestment.add(valueAtRisk);
                insight = "Below minimum. Suggested purchase is based on the configured recovery target.";
            } else if (current.compareTo(max) > 0) {
                over++;
                severity = "MEDIUM";
                valueAtRisk = current.subtract(max).multiply(price);
                overstockCapital = overstockCapital.add(valueAtRisk);
                insight = "Above maximum. This capital may be tied up in slow-moving stock.";
            } else {
                healthy++;
            }

            if (severity != null) {
                risks.add(new InventoryRiskResponse(
                        sku.getId(),
                        sku.getName(),
                        severity,
                        current,
                        min,
                        max,
                        moneyValue(valueAtRisk),
                        insight
                ));
            }
        }

        Map<String, Integer> severityOrder = Map.of("CRITICAL", 0, "HIGH", 1, "MEDIUM", 2);
        risks.sort(
                Comparator.comparingInt((InventoryRiskResponse risk) -> severityOrder.getOrDefault(risk.severity(), 99))
                        .thenComparing(InventoryRiskResponse::estimatedValueAtRiskRm, Comparator.reverseOrder())
                        .thenComparing(InventoryRiskResponse::skuName, String.CASE_INSENSITIVE_ORDER)
        );
        double score = skus.isEmpty() ? 100.0 : healthy * 100.0 / skus.size();
        return new InventoryIntelligenceResponse(
                skus.size(),
                healthy,
                low,
                out,
                over,
                moneyValue(stockValue),
                moneyValue(reorderInvestment),
                moneyValue(overstockCapital),
                BigDecimal.valueOf(score).setScale(1, RoundingMode.HALF_UP),
                List.copyOf(risks.stream().limit(5).toList())
        );
    }

    private WasteOverviewResponse wasteOverview(
            LocalDate today,
            List<BusinessReport> reports,
            Map<UUID, WasteReportDetail> details,
            BigDecimal periodNetSales
    ) {
        BigDecimal todayLoss = BigDecimal.ZERO;
        BigDecimal periodLoss = BigDecimal.ZERO;
        Map<String, BigDecimal> byItem = new HashMap<>();
        for (BusinessReport report : reports) {
            WasteReportDetail detail = details.get(report.getId());
            if (detail == null) continue;
            BigDecimal loss = detail.estimatedLossRm();
            periodLoss = periodLoss.add(loss);
            if (report.getReportDate().equals(today)) todayLoss = todayLoss.add(loss);
            byItem.merge(detail.getItemName(), loss, BigDecimal::add);
        }
        Map.Entry<String, BigDecimal> top = byItem.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .orElse(Map.entry("None", BigDecimal.ZERO));
        return new WasteOverviewResponse(
                moneyValue(todayLoss),
                moneyValue(periodLoss),
                percentValue(percentage(periodLoss, periodNetSales)),
                top.getKey(),
                moneyValue(top.getValue())
        );
    }

    private DailyPhotoOverviewResponse dailyPhotoOverview(AuthenticatedUser principal, LocalDate today) {
        Optional<BusinessReport> current = reportRepository
                .findByTenantIdAndReportTypeAndReportDateAndSubmittedByUserId(
                        principal.tenantId(), BusinessReportType.DAILY_PHOTO, today, principal.userId()
                );
        int currentCount = current
                .map(report -> (int) dailyPhotoRepository.countByTenantIdAndReportId(principal.tenantId(), report.getId()))
                .orElse(0);
        int required = properties.getDailyPhotoMinimum();

        if (!isManagement(principal.systemRole())) {
            return new DailyPhotoOverviewResponse(
                    currentCount,
                    required,
                    currentCount >= required,
                    0,
                    0,
                    0
            );
        }

        List<UserAccount> eligible = userRepository
                .findAllByTenant_IdAndActiveTrueOrderByIdentity_FullNameAsc(principal.tenantId())
                .stream()
                .filter(this::requiresDailyPhotos)
                .toList();
        int completed = 0;
        for (UserAccount user : eligible) {
            Optional<BusinessReport> report = reportRepository
                    .findByTenantIdAndReportTypeAndReportDateAndSubmittedByUserId(
                            principal.tenantId(), BusinessReportType.DAILY_PHOTO, today, user.getId()
                    );
            if (report.isPresent()) {
                long count = dailyPhotoRepository.countByTenantIdAndReportId(principal.tenantId(), report.get().getId());
                if (count >= required && report.get().getWorkflowStatus() == ReportWorkflowStatus.APPROVED) {
                    completed++;
                }
            }
        }
        double completion = eligible.isEmpty() ? 100.0 : completed * 100.0 / eligible.size();
        return new DailyPhotoOverviewResponse(
                currentCount,
                required,
                currentCount >= required,
                eligible.size(),
                completed,
                BigDecimal.valueOf(completion).setScale(1, RoundingMode.HALF_UP).doubleValue()
        );
    }

    private ComplaintOverviewResponse complaintOverview(Iterable<ComplaintReportDetail> details) {
        long open = 0;
        long resolved = 0;
        BigDecimal compensation = BigDecimal.ZERO;
        for (ComplaintReportDetail detail : details) {
            if (detail.getComplaintStatus() == ComplaintStatus.OPEN) open++;
            if (detail.getComplaintStatus() == ComplaintStatus.RESOLVED) resolved++;
            if (detail.getCompensationAmountRm() != null) {
                compensation = compensation.add(detail.getCompensationAmountRm());
            }
        }
        long total = open + resolved;
        double resolution = total == 0 ? 100.0 : resolved * 100.0 / total;
        return new ComplaintOverviewResponse(
                open,
                resolved,
                BigDecimal.valueOf(resolution).setScale(1, RoundingMode.HALF_UP).doubleValue(),
                moneyValue(compensation)
        );
    }

    private List<ReportTrendPointResponse> buildTrend(
            LocalDate from,
            LocalDate to,
            List<BusinessReport> salesReports,
            Map<UUID, SalesReportDetail> sales,
            Map<UUID, BigDecimal> voids,
            List<BusinessReport> wasteReports,
            Map<UUID, WasteReportDetail> waste
    ) {
        Map<LocalDate, BigDecimal> netByDate = new HashMap<>();
        Map<LocalDate, BigDecimal> voidByDate = new HashMap<>();
        Map<LocalDate, BigDecimal> wasteByDate = new HashMap<>();
        for (BusinessReport report : salesReports) {
            SalesReportDetail detail = sales.get(report.getId());
            if (detail == null) continue;
            BigDecimal voidAmount = voids.getOrDefault(report.getId(), BigDecimal.ZERO);
            netByDate.merge(report.getReportDate(), detail.grossSalesRm().max(BigDecimal.ZERO), BigDecimal::add);
            voidByDate.merge(report.getReportDate(), voidAmount, BigDecimal::add);
        }
        for (BusinessReport report : wasteReports) {
            WasteReportDetail detail = waste.get(report.getId());
            if (detail != null) {
                wasteByDate.merge(report.getReportDate(), detail.estimatedLossRm(), BigDecimal::add);
            }
        }
        List<ReportTrendPointResponse> result = new ArrayList<>();
        LocalDate cursor = from;
        while (!cursor.isAfter(to)) {
            result.add(new ReportTrendPointResponse(
                    cursor,
                    moneyValue(netByDate.getOrDefault(cursor, BigDecimal.ZERO)),
                    moneyValue(voidByDate.getOrDefault(cursor, BigDecimal.ZERO)),
                    moneyValue(wasteByDate.getOrDefault(cursor, BigDecimal.ZERO))
            ));
            cursor = cursor.plusDays(1);
        }
        return List.copyOf(result);
    }

    private SalesReportResponse toSalesResponse(BusinessReport report, Map<UUID, String> names) {
        SalesReportDetail detail = salesRepository.findByReportIdAndTenantId(
                report.getId(), report.getTenantId()
        ).orElse(null);
        List<SalesVoidBill> voidBills = voidBillRepository
                .findAllByTenantIdAndSalesReportIdOrderByCreatedAtAsc(report.getTenantId(), report.getId());
        Map<UUID, ReportMedia> media = mediaById(
                report.getTenantId(),
                voidBills.stream().map(SalesVoidBill::getPhotoMediaId).toList()
        );
        List<VoidBillResponse> voidResponses = voidBills.stream()
                .map(item -> toVoidBillResponse(item, media.get(item.getPhotoMediaId()), names))
                .toList();
        BigDecimal cashTotal = detail == null ? BigDecimal.ZERO : detail.getSubTotalRm();
        String cashReceivedBy = detail == null ? "" : detail.getCashReceivedBy();
        BigDecimal foodDelivery = detail == null ? BigDecimal.ZERO : detail.getPandaSalesRm();
        BigDecimal ewalletTotal = detail == null ? BigDecimal.ZERO : detail.getEwalletTotalRm();
        BigDecimal totalSales = detail == null ? BigDecimal.ZERO : detail.getSalesRm();
        BigDecimal voidTotal = voidBills.stream()
                .map(SalesVoidBill::getAmountRm)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int staffOnDuty = detail == null ? 0 : detail.getStaffCount();
        BigDecimal salesPerStaff = staffOnDuty == 0
                ? BigDecimal.ZERO
                : totalSales.divide(BigDecimal.valueOf(staffOnDuty), 2, RoundingMode.HALF_UP);
        return new SalesReportResponse(
                report.getId(),
                report.getReportDate(),
                report.getWorkflowStatus(),
                moneyValue(cashTotal),
                cashReceivedBy,
                moneyValue(foodDelivery),
                moneyValue(ewalletTotal),
                moneyValue(totalSales),
                moneyValue(voidTotal),
                staffOnDuty,
                moneyValue(salesPerStaff),
                percentValue(percentage(voidTotal, totalSales.add(voidTotal))),
                userName(report.getSubmittedByUserId(), names),
                report.getSubmittedAt(),
                userNameNullable(report.getReviewedByUserId(), names),
                report.getReviewNote(),
                List.copyOf(voidResponses)
        );
    }

    private SalesReportResponse emptySales(LocalDate date) {
        BigDecimal zero = BigDecimal.ZERO.setScale(2);
        return new SalesReportResponse(
                null, date, ReportWorkflowStatus.DRAFT,
                zero, "", zero, zero, zero, zero, 0, zero,
                BigDecimal.ZERO.setScale(1), null, null, null, null, List.of()
        );
    }

    private VoidBillResponse toVoidBillResponse(
            SalesVoidBill item,
            ReportMedia media,
            Map<UUID, String> names
    ) {
        return new VoidBillResponse(
                item.getId(),
                item.getBillNumber(),
                item.getReason(),
                moneyValue(item.getAmountRm()),
                media == null ? "" : media.getStorageKey(),
                userName(item.getCreatedByUserId(), names),
                item.getCreatedAt()
        );
    }

    private WasteReportResponse toWasteResponse(
            BusinessReport report,
            WasteReportDetail detail,
            ReportMedia media,
            Map<UUID, String> names
    ) {
        return new WasteReportResponse(
                report.getId(),
                report.getReportDate(),
                report.getWorkflowStatus(),
                detail.getSkuId(),
                detail.getItemName(),
                detail.getQuantity(),
                detail.getUnit(),
                moneyValue(detail.getEstimatedUnitCostRm()),
                moneyValue(detail.estimatedLossRm()),
                detail.getReason(),
                media == null ? "" : media.getStorageKey(),
                userName(report.getSubmittedByUserId(), names),
                report.getSubmittedAt(),
                userNameNullable(report.getReviewedByUserId(), names),
                report.getReviewNote()
        );
    }

    private DailyPhotoReportResponse toDailyPhotoResponse(BusinessReport report, String userName) {
        List<DailyReportPhoto> photos = dailyPhotoRepository
                .findAllByTenantIdAndReportIdOrderByCreatedAtAsc(report.getTenantId(), report.getId());
        Map<UUID, ReportMedia> media = mediaById(
                report.getTenantId(),
                photos.stream().map(DailyReportPhoto::getPhotoMediaId).toList()
        );
        List<DailyPhotoItemResponse> items = photos.stream()
                .map(photo -> new DailyPhotoItemResponse(
                        photo.getId(),
                        Optional.ofNullable(media.get(photo.getPhotoMediaId()))
                                .map(ReportMedia::getStorageKey)
                                .orElse(""),
                        photo.getCreatedAt()
                ))
                .toList();
        Map<UUID, String> names = userNames(report.getTenantId());
        int minimum = properties.getDailyPhotoMinimum();
        return new DailyPhotoReportResponse(
                report.getId(),
                report.getReportDate(),
                report.getWorkflowStatus(),
                report.getSubmittedByUserId(),
                userName,
                items.size(),
                minimum,
                items.size() >= minimum,
                report.getSubmittedAt(),
                userNameNullable(report.getReviewedByUserId(), names),
                report.getReviewNote(),
                List.copyOf(items)
        );
    }

    private DailyPhotoReportResponse emptyDailyPhoto(LocalDate date, UserAccount user) {
        return new DailyPhotoReportResponse(
                null,
                date,
                ReportWorkflowStatus.DRAFT,
                user.getId(),
                user.getFullName(),
                0,
                properties.getDailyPhotoMinimum(),
                false,
                null,
                null,
                null,
                List.of()
        );
    }

    private ComplaintReportResponse toComplaintResponse(
            BusinessReport report,
            ComplaintReportDetail detail,
            ReportMedia media,
            Map<UUID, String> names
    ) {
        return new ComplaintReportResponse(
                report.getId(),
                report.getReportDate(),
                detail.getComplaintStatus(),
                media == null ? "" : media.getStorageKey(),
                detail.getCustomerGender(),
                detail.getEstimatedAge(),
                detail.getComplaintInfo(),
                detail.getPhoneE164(),
                detail.getActionTaken(),
                detail.getCompensationAmountRm() == null ? null : moneyValue(detail.getCompensationAmountRm()),
                userName(report.getSubmittedByUserId(), names),
                report.getSubmittedAt(),
                detail.getResolvedAt()
        );
    }

    private ApprovalReportResponse approvalsForSingle(BusinessReport report, Map<UUID, String> names) {
        BigDecimal amount = BigDecimal.ZERO;
        int evidenceCount = 0;
        String summary = report.getReportType().name();
        if (report.getReportType() == BusinessReportType.SALES) {
            SalesReportDetail detail = salesRepository.findByReportIdAndTenantId(report.getId(), report.getTenantId()).orElse(null);
            BigDecimal voidTotal = voidBillRepository
                    .findAllByTenantIdAndSalesReportIdOrderByCreatedAtAsc(report.getTenantId(), report.getId())
                    .stream().map(SalesVoidBill::getAmountRm).reduce(BigDecimal.ZERO, BigDecimal::add);
            amount = detail == null ? BigDecimal.ZERO : detail.grossSalesRm();
            evidenceCount = voidBillRepository
                    .findAllByTenantIdAndSalesReportIdOrderByCreatedAtAsc(report.getTenantId(), report.getId()).size();
            summary = "Total sales RM " + money(amount);
        } else if (report.getReportType() == BusinessReportType.WASTE) {
            WasteReportDetail detail = wasteRepository.findByReportIdAndTenantId(report.getId(), report.getTenantId()).orElse(null);
            if (detail != null) {
                amount = detail.estimatedLossRm();
                evidenceCount = 1;
                summary = detail.getItemName() + " · RM " + money(amount);
            }
        } else if (report.getReportType() == BusinessReportType.DAILY_PHOTO) {
            evidenceCount = (int) dailyPhotoRepository.countByTenantIdAndReportId(report.getTenantId(), report.getId());
            summary = evidenceCount + " daily photos";
        }
        return new ApprovalReportResponse(
                report.getId(),
                report.getReportType(),
                report.getReportDate(),
                report.getSubmittedByUserId(),
                userName(report.getSubmittedByUserId(), names),
                report.getSubmittedAt(),
                summary,
                moneyValue(amount),
                evidenceCount
        );
    }

    private Map<UUID, SalesReportDetail> salesDetails(List<BusinessReport> reports) {
        List<UUID> ids = reportIds(reports);
        if (ids.isEmpty()) return Map.of();
        return salesRepository.findAllByTenantIdAndReportIdIn(reports.get(0).getTenantId(), ids)
                .stream().collect(Collectors.toMap(SalesReportDetail::getReportId, Function.identity()));
    }

    private Map<UUID, BigDecimal> voidTotals(List<BusinessReport> reports) {
        List<UUID> ids = reportIds(reports);
        if (ids.isEmpty()) return Map.of();
        Map<UUID, BigDecimal> totals = new HashMap<>();
        for (SalesVoidBill item : voidBillRepository.findAllByTenantIdAndSalesReportIdIn(reports.get(0).getTenantId(), ids)) {
            totals.merge(item.getSalesReportId(), item.getAmountRm(), BigDecimal::add);
        }
        return totals;
    }

    private Map<UUID, WasteReportDetail> wasteDetails(List<BusinessReport> reports) {
        List<UUID> ids = reportIds(reports);
        if (ids.isEmpty()) return Map.of();
        return wasteRepository.findAllByTenantIdAndReportIdIn(reports.get(0).getTenantId(), ids)
                .stream().collect(Collectors.toMap(WasteReportDetail::getReportId, Function.identity()));
    }

    private Map<UUID, ComplaintReportDetail> complaintDetails(List<BusinessReport> reports) {
        List<UUID> ids = reportIds(reports);
        if (ids.isEmpty()) return Map.of();
        return complaintRepository.findAllByTenantIdAndReportIdIn(reports.get(0).getTenantId(), ids)
                .stream().collect(Collectors.toMap(ComplaintReportDetail::getReportId, Function.identity()));
    }

    private Map<UUID, ReportMedia> mediaById(UUID tenantId, List<UUID> ids) {
        List<UUID> distinct = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) return Map.of();
        return mediaRepository.findAllByTenantIdAndIdIn(tenantId, distinct)
                .stream().collect(Collectors.toMap(ReportMedia::getId, Function.identity()));
    }

    private Map<UUID, String> userNames(UUID tenantId) {
        return userRepository.findAllByTenant_IdOrderByIdentity_FullNameAsc(tenantId)
                .stream().collect(Collectors.toMap(
                        UserAccount::getId,
                        UserAccount::getFullName,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    private BigDecimal netSalesTotal(
            List<BusinessReport> reports,
            Map<UUID, SalesReportDetail> details,
            Map<UUID, BigDecimal> voidTotals
    ) {
        BigDecimal total = BigDecimal.ZERO;
        for (BusinessReport report : reports) {
            SalesReportDetail detail = details.get(report.getId());
            if (detail == null) continue;
            total = total.add(detail.grossSalesRm());
        }
        return total;
    }

    private BigDecimal recoveryTarget(StockSku sku) {
        BigDecimal range = sku.getMaximumBalanceValue().subtract(sku.getMinimumBalanceValue());
        BigDecimal recovery = range
                .multiply(BigDecimal.valueOf(sku.getRecoveryPercent()))
                .divide(HUNDRED, 4, RoundingMode.HALF_UP);
        return sku.getMinimumBalanceValue().add(recovery);
    }

    private boolean includedForAnalytics(BusinessReport report) {
        return ANALYTICS_STATUSES.contains(report.getWorkflowStatus());
    }

    private boolean requiresDailyPhotos(UserAccount user) {
        SystemRole role = user.getRole().getSystemKey();
        return role != SystemRole.OWNER && role != SystemRole.HEAD;
    }

    private BusinessReport requireReport(
            AuthenticatedUser principal,
            UUID reportId,
            BusinessReportType expectedType
    ) {
        BusinessReport report = reportRepository.findByIdAndTenantId(reportId, principal.tenantId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "REPORT_NOT_FOUND", "Report was not found."));
        if (report.getReportType() != expectedType) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REPORT_TYPE_MISMATCH", "The selected report has the wrong type.");
        }
        return report;
    }

    private void requireManagement(AuthenticatedUser principal) {
        if (!isManagement(principal.systemRole())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "REPORT_ACCESS_DENIED", "This report is available to management only.");
        }
    }

    private void requireReviewer(AuthenticatedUser principal) {
        SystemRole role = principal.systemRole();
        if (role != SystemRole.OWNER && role != SystemRole.HEAD && role != SystemRole.MANAGER) {
            throw new ApiException(HttpStatus.FORBIDDEN, "REPORT_REVIEW_ACCESS_DENIED", "Only Owner, Head or Manager can review reports.");
        }
    }

    private boolean isManagement(SystemRole role) {
        return role == SystemRole.OWNER
                || role == SystemRole.HEAD
                || role == SystemRole.MANAGER
                || role == SystemRole.SUPERVISOR;
    }

    private boolean isSeniorManagement(SystemRole role) {
        return role == SystemRole.OWNER || role == SystemRole.HEAD;
    }

    private void validateEditableDate(AuthenticatedUser principal, LocalDate reportDate) {
        Objects.requireNonNull(reportDate, "reportDate must not be null");
        LocalDate today = today();
        if (reportDate.isAfter(today)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FUTURE_REPORT_DATE", "A report cannot be dated in the future.");
        }
        if (isSeniorManagement(principal.systemRole())) return;
        if (principal.systemRole() == SystemRole.MANAGER || principal.systemRole() == SystemRole.SUPERVISOR) {
            if (reportDate.isBefore(today.minusDays(7))) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "REPORT_DATE_TOO_OLD", "Managers and supervisors can backdate reports by up to 7 days.");
            }
            return;
        }
        if (!reportDate.equals(today)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REPORT_DATE_TODAY_ONLY", "Staff can submit reports for today only.");
        }
    }

    private void validateViewDate(LocalDate date) {
        if (date.isAfter(today())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FUTURE_REPORT_DATE", "A report cannot be dated in the future.");
        }
    }

    private DateRange dateRange(LocalDate from, LocalDate to, int maxDays) {
        LocalDate resolvedTo = to == null ? today() : to;
        LocalDate resolvedFrom = from == null ? resolvedTo.minusDays(Math.min(6, maxDays - 1L)) : from;
        if (resolvedTo.isAfter(today())) resolvedTo = today();
        if (resolvedFrom.isAfter(resolvedTo)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE", "From date must not be after to date.");
        }
        if (resolvedFrom.isBefore(resolvedTo.minusDays(maxDays - 1L))) {
            resolvedFrom = resolvedTo.minusDays(maxDays - 1L);
        }
        return new DateRange(resolvedFrom, resolvedTo);
    }

    private LocalDate today() {
        return LocalDate.now(properties.zoneId());
    }

    private static List<UUID> reportIds(List<BusinessReport> reports) {
        return reports.stream().map(BusinessReport::getId).toList();
    }

    private static BigDecimal midpoint(BigDecimal minimum, BigDecimal maximum) {
        return minimum.add(maximum).divide(TWO, 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal percentage(BigDecimal numerator, BigDecimal denominator) {
        if (denominator == null || denominator.signum() == 0) return BigDecimal.ZERO;
        return numerator.divide(denominator, 4, RoundingMode.HALF_UP).multiply(HUNDRED);
    }

    private static BigDecimal moneyValue(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal percentValue(BigDecimal value) {
        return value.setScale(1, RoundingMode.HALF_UP);
    }

    private static String money(BigDecimal value) {
        return moneyValue(value).toPlainString();
    }

    private static String userName(UUID userId, Map<UUID, String> names) {
        return names.getOrDefault(userId, userId == null ? "Unknown" : userId.toString());
    }

    private static String userNameNullable(UUID userId, Map<UUID, String> names) {
        return userId == null ? null : userName(userId, names);
    }

    private static ApiException locked(String message) {
        return new ApiException(HttpStatus.CONFLICT, "REPORT_LOCKED", message);
    }

    private record DateRange(LocalDate from, LocalDate to) {
    }
}
