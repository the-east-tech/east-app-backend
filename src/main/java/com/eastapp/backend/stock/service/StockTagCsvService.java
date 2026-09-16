package com.eastapp.backend.stock.service;

import com.eastapp.backend.auth.security.AuthenticatedUser;
import com.eastapp.backend.common.api.CsvImportResponse;
import com.eastapp.backend.common.api.CsvPreviewResponse;
import com.eastapp.backend.common.csv.CsvSupport;
import com.eastapp.backend.common.error.ApiException;
import com.eastapp.backend.people.UserAccount;
import com.eastapp.backend.people.UserAccountRepository;
import com.eastapp.backend.stock.StockTag;
import com.eastapp.backend.stock.StockTagAssignee;
import com.eastapp.backend.stock.StockTagAssigneeRepository;
import com.eastapp.backend.stock.StockTagRepository;
import com.eastapp.backend.stock.api.CreateStockTagRequest;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class StockTagCsvService {
    private static final String FORMAT = "EASTAPP_TAG_CSV";
    private static final int VERSION = 1;
    private static final List<String> HEADERS = List.of(
            "eastapp_format", "format_version", "languages", "tag",
            "assigned_employee_ids", "active"
    );
    private final StockTagRepository tagRepository;
    private final StockTagAssigneeRepository assigneeRepository;
    private final UserAccountRepository userRepository;
    private final StockService stockService;

    public StockTagCsvService(
            StockTagRepository tagRepository,
            StockTagAssigneeRepository assigneeRepository,
            UserAccountRepository userRepository,
            StockService stockService
    ) {
        this.tagRepository = tagRepository;
        this.assigneeRepository = assigneeRepository;
        this.userRepository = userRepository;
        this.stockService = stockService;
    }

    @Transactional(readOnly = true)
    public CsvExport exportTags(AuthenticatedUser principal) {
        requireOwner(principal);
        List<StockTag> tags = tagRepository.findAllByTenant_IdOrderByTagAsc(principal.tenantId());
        Map<UUID, String> employees = new HashMap<>();
        userRepository.findAllByTenant_IdAndActiveTrueOrderByIdentity_FullNameAsc(principal.tenantId())
                .forEach(user -> employees.put(user.getId(), user.getEmployeeId()));
        Map<UUID, List<String>> assignees = new HashMap<>();
        if (!tags.isEmpty()) {
            assigneeRepository.findAllByTenantIdAndTagIdIn(
                    principal.tenantId(), tags.stream().map(StockTag::getId).toList()
            ).forEach(link -> {
                String employeeId = employees.get(link.getUserId());
                if (employeeId != null) assignees.computeIfAbsent(link.getTagId(), ignored -> new ArrayList<>()).add(employeeId);
            });
        }
        try {
            StringWriter writer = new StringWriter();
            writer.write('\ufeff');
            try (CSVPrinter printer = new CSVPrinter(writer, CSVFormat.RFC4180.builder()
                    .setHeader(HEADERS.toArray(String[]::new))
                    .setRecordSeparator("\r\n")
                    .get())) {
                for (StockTag tag : tags) {
                    printer.printRecord(FORMAT, VERSION, "ENGLISH|CHINESE", tag.getTag(),
                            String.join("|", assignees.getOrDefault(tag.getId(), List.of())), tag.isActive());
                }
            }
            return new CsvExport("eastapp-tags-" + LocalDate.now(ZoneId.of("Asia/Kuala_Lumpur")) + ".csv",
                    writer.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "TAG_CSV_EXPORT_FAILED", "The Tag CSV could not be generated.");
        }
    }

    @Transactional(readOnly = true)
    public CsvPreviewResponse preview(AuthenticatedUser principal, MultipartFile file) {
        requireOwner(principal);
        return analyse(principal, file).preview();
    }

    @Transactional
    public CsvImportResponse importTags(AuthenticatedUser principal, MultipartFile file) {
        requireOwner(principal);
        Analysis analysis = analyse(principal, file);
        if (analysis.invalid() > 0) throw CsvSupport.badRequest("TAG_CSV_INVALID_ROWS", "Fix every invalid CSV row before importing.");
        for (ParsedTag row : analysis.ready()) {
            stockService.createTag(principal, new CreateStockTagRequest(row.tag(), row.userIds(), row.active()));
        }
        return new CsvImportResponse(analysis.ready().size(), analysis.duplicates());
    }

    private Analysis analyse(AuthenticatedUser principal, MultipartFile file) {
        Set<String> existing = new HashSet<>();
        tagRepository.findAllByTenant_IdOrderByTagAsc(principal.tenantId())
                .forEach(tag -> existing.add(normalise(tag.getTag())));
        Map<String, UUID> users = new HashMap<>();
        userRepository.findAllByTenant_IdAndActiveTrueOrderByIdentity_FullNameAsc(principal.tenantId())
                .forEach(user -> users.put(normalise(user.getEmployeeId()), user.getId()));
        List<ParsedTag> ready = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int invalid = 0;
        int duplicates = 0;
        List<CSVRecord> records = CsvSupport.records(file, HEADERS, FORMAT, VERSION);
        for (CSVRecord record : records) {
            int line = Math.toIntExact(record.getRecordNumber() + 1);
            try {
                String tag = CsvSupport.text(record, "tag");
                if (tag.isBlank() || tag.length() > 80) throw new IllegalArgumentException("tag must contain 1 to 80 characters");
                String key = normalise(tag);
                if (existing.contains(key) || !seen.add(key)) {
                    duplicates++;
                    continue;
                }
                List<UUID> userIds = new ArrayList<>();
                String assigned = CsvSupport.text(record, "assigned_employee_ids");
                if (!assigned.isBlank()) {
                    for (String employeeId : assigned.split("\\|")) {
                        UUID userId = users.get(normalise(employeeId));
                        if (userId == null) throw new IllegalArgumentException("unknown active employee ID: " + employeeId.trim());
                        if (!userIds.contains(userId)) userIds.add(userId);
                    }
                }
                ready.add(new ParsedTag(tag, List.copyOf(userIds), CsvSupport.bool(record, "active")));
            } catch (RuntimeException exception) {
                invalid++;
                if (errors.size() < CsvSupport.MAX_MESSAGES) errors.add("Row " + line + ": " + exception.getMessage());
            }
        }
        return new Analysis(records.size(), ready, duplicates, invalid, errors);
    }

    private static String normalise(String value) { return value.trim().toLowerCase(Locale.ROOT); }
    private static void requireOwner(AuthenticatedUser principal) {
        if (!principal.isOwner()) throw new ApiException(HttpStatus.FORBIDDEN, "TAG_CSV_OWNER_REQUIRED", "Only Owner can import or export Tags.");
    }

    public record CsvExport(String fileName, byte[] bytes) {}
    private record ParsedTag(String tag, List<UUID> userIds, boolean active) {}
    private record Analysis(int total, List<ParsedTag> ready, int duplicates, int invalid, List<String> errors) {
        CsvPreviewResponse preview() { return new CsvPreviewResponse(FORMAT, VERSION, total, ready.size(), duplicates, invalid(), errors); }
    }
}
