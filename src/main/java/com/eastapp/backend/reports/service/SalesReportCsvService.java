package com.eastapp.backend.reports.service;

import com.eastapp.backend.auth.security.AuthenticatedUser;
import com.eastapp.backend.common.api.CsvImportResponse;
import com.eastapp.backend.common.api.CsvPreviewResponse;
import com.eastapp.backend.common.csv.CsvSupport;
import com.eastapp.backend.common.error.ApiException;
import com.eastapp.backend.people.SystemRole;
import com.eastapp.backend.people.UserAccount;
import com.eastapp.backend.people.UserAccountRepository;
import com.eastapp.backend.reports.BusinessReportRepository;
import com.eastapp.backend.reports.BusinessReportType;
import com.eastapp.backend.reports.api.SalesReportResponse;
import com.eastapp.backend.reports.api.UpsertSalesReportRequest;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class SalesReportCsvService {
    private static final String FORMAT = "EASTAPP_SALES_REPORT_CSV";
    private static final int VERSION = 1;
    private static final Set<SystemRole> CASH_ROLES = Set.of(SystemRole.OWNER, SystemRole.HEAD, SystemRole.MANAGER);
    private static final List<String> HEADERS = List.of(
            "eastapp_format", "format_version", "languages", "report_date", "workflow_status",
            "cash_total_rm", "cash_received_by_employee_id", "cash_received_by",
            "food_delivery_sales_rm", "ewallet_total_rm", "staff_on_duty", "total_sales_rm",
            "void_total_rm", "void_bill_count"
    );
    private final BusinessReportService reportService;
    private final BusinessReportRepository reportRepository;
    private final UserAccountRepository userRepository;

    public SalesReportCsvService(
            BusinessReportService reportService,
            BusinessReportRepository reportRepository,
            UserAccountRepository userRepository
    ) {
        this.reportService = reportService;
        this.reportRepository = reportRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public CsvExport exportSales(AuthenticatedUser principal, LocalDate from, LocalDate to) {
        List<SalesReportResponse> reports = reportService.salesHistory(principal, from, to, null);
        Map<UUID, String> employeeIds = new HashMap<>();
        userRepository.findAllByTenant_IdOrderByIdentity_FullNameAsc(principal.tenantId())
                .forEach(user -> employeeIds.put(user.getId(), user.getEmployeeId()));
        try {
            StringWriter writer = new StringWriter();
            writer.write('\ufeff');
            try (CSVPrinter printer = new CSVPrinter(writer, CSVFormat.RFC4180.builder()
                    .setHeader(HEADERS.toArray(String[]::new))
                    .setRecordSeparator("\r\n")
                    .get())) {
                for (SalesReportResponse report : reports) {
                    printer.printRecord(
                            FORMAT, VERSION, "ENGLISH|CHINESE", report.reportDate(), report.workflowStatus(),
                            report.cashTotalRm(), employeeIds.getOrDefault(report.cashReceivedByUserId(), ""), report.cashReceivedBy(),
                            report.foodDeliverySalesRm(), report.ewalletTotalRm(), report.staffOnDuty(), report.totalSalesRm(),
                            report.voidTotalRm(), report.voidBills().size()
                    );
                }
            }
            return new CsvExport("eastapp-sales-reports-" + LocalDate.now(ZoneId.of("Asia/Kuala_Lumpur")) + ".csv",
                    writer.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "SALES_CSV_EXPORT_FAILED", "The Sales Report CSV could not be generated.");
        }
    }

    @Transactional(readOnly = true)
    public CsvPreviewResponse preview(AuthenticatedUser principal, MultipartFile file) {
        return analyse(principal, file).preview();
    }

    @Transactional
    public CsvImportResponse importSales(AuthenticatedUser principal, MultipartFile file) {
        Analysis analysis = analyse(principal, file);
        if (analysis.invalid() > 0) throw CsvSupport.badRequest("SALES_CSV_INVALID_ROWS", "Fix every invalid CSV row before importing.");
        for (ParsedSales row : analysis.ready()) {
            reportService.importSales(principal, new UpsertSalesReportRequest(
                    row.date(), row.cash(), row.receiverId(), row.delivery(), row.ewallet(), row.staff()
            ));
        }
        return new CsvImportResponse(analysis.ready().size(), analysis.duplicates());
    }

    private Analysis analyse(AuthenticatedUser principal, MultipartFile file) {
        Map<String, UserAccount> receivers = new HashMap<>();
        userRepository.findAllByTenant_IdAndActiveTrueOrderByIdentity_FullNameAsc(principal.tenantId()).stream()
                .filter(user -> CASH_ROLES.contains(user.getRole().getSystemKey()))
                .forEach(user -> receivers.put(normalise(user.getEmployeeId()), user));
        Set<LocalDate> seen = new HashSet<>();
        List<ParsedSales> ready = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        int invalid = 0;
        int duplicates = 0;
        List<CSVRecord> records = CsvSupport.records(file, HEADERS, FORMAT, VERSION);
        for (CSVRecord record : records) {
            int line = Math.toIntExact(record.getRecordNumber() + 1);
            try {
                LocalDate date = LocalDate.parse(CsvSupport.text(record, "report_date"));
                if (date.isAfter(LocalDate.now(ZoneId.of("Asia/Kuala_Lumpur")))) {
                    throw new IllegalArgumentException("report_date must not be in the future");
                }
                if (reportRepository.findByTenantIdAndReportTypeAndReportDate(principal.tenantId(), BusinessReportType.SALES, date).isPresent()
                        || !seen.add(date)) {
                    duplicates++;
                    continue;
                }
                UserAccount receiver = receivers.get(normalise(CsvSupport.text(record, "cash_received_by_employee_id")));
                if (receiver == null) throw new IllegalArgumentException("cash_received_by_employee_id must identify an active Owner, Head, or Manager");
                BigDecimal cash = money(record, "cash_total_rm");
                BigDecimal delivery = money(record, "food_delivery_sales_rm");
                BigDecimal ewallet = money(record, "ewallet_total_rm");
                int staff = Integer.parseInt(CsvSupport.text(record, "staff_on_duty"));
                if (staff < 1 || staff > 500) throw new IllegalArgumentException("staff_on_duty must be between 1 and 500");
                ready.add(new ParsedSales(date, cash, receiver.getId(), delivery, ewallet, staff));
            } catch (DateTimeParseException exception) {
                invalid++;
                if (errors.size() < CsvSupport.MAX_MESSAGES) errors.add("Row " + line + ": report_date must use YYYY-MM-DD");
            } catch (RuntimeException exception) {
                invalid++;
                if (errors.size() < CsvSupport.MAX_MESSAGES) errors.add("Row " + line + ": " + exception.getMessage());
            }
        }
        return new Analysis(records.size(), ready, duplicates, invalid, errors);
    }

    private static BigDecimal money(CSVRecord record, String column) {
        try {
            BigDecimal value = new BigDecimal(CsvSupport.text(record, column));
            if (value.signum() < 0 || value.scale() > 2 || value.precision() - value.scale() > 12) {
                throw new IllegalArgumentException(column + " must be a non-negative amount with at most 2 decimals");
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(column + " must be a number");
        }
    }

    private static String normalise(String value) { return value.trim().toLowerCase(Locale.ROOT); }
    public record CsvExport(String fileName, byte[] bytes) {}
    private record ParsedSales(LocalDate date, BigDecimal cash, UUID receiverId, BigDecimal delivery, BigDecimal ewallet, int staff) {}
    private record Analysis(int total, List<ParsedSales> ready, int duplicates, int invalid, List<String> errors) {
        CsvPreviewResponse preview() { return new CsvPreviewResponse(FORMAT, VERSION, total, ready.size(), duplicates, invalid, errors); }
    }
}
