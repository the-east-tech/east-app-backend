package com.eastapp.backend.stock.service;

import com.eastapp.backend.activity.service.WorkflowActivityService;
import com.eastapp.backend.auth.security.AuthenticatedUser;
import com.eastapp.backend.common.error.ApiException;
import com.eastapp.backend.organisation.Tenant;
import com.eastapp.backend.organisation.TenantRepository;
import com.eastapp.backend.people.UserAccount;
import com.eastapp.backend.people.UserAccountRepository;
import com.eastapp.backend.stock.StockCheckSchedule;
import com.eastapp.backend.stock.StockMedia;
import com.eastapp.backend.stock.StockMediaRepository;
import com.eastapp.backend.stock.StockSku;
import com.eastapp.backend.stock.StockSkuCsvOperation;
import com.eastapp.backend.stock.StockSkuCsvRequest;
import com.eastapp.backend.stock.StockSkuCsvRequestRepository;
import com.eastapp.backend.stock.StockSkuCsvRequestStatus;
import com.eastapp.backend.stock.StockSkuExportSnapshot;
import com.eastapp.backend.stock.StockSkuExportSnapshotRepository;
import com.eastapp.backend.stock.StockSkuRepository;
import com.eastapp.backend.stock.StockSupplier;
import com.eastapp.backend.stock.StockSupplierRepository;
import com.eastapp.backend.stock.StockTag;
import com.eastapp.backend.stock.StockTagRepository;
import com.eastapp.backend.stock.api.StockSkuCsvPreviewResponse;
import com.eastapp.backend.stock.api.StockSkuCsvRequestResponse;
import com.eastapp.backend.stock.api.ReviewStockSkuCsvRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class StockSkuCsvService {
    private static final String FORMAT_NAME = "EASTAPP_SKU_CSV";
    private static final String LANGUAGES = "ENGLISH|CHINESE";
    private static final int MAX_FILE_BYTES = 2 * 1024 * 1024;
    private static final int MAX_ROWS = 1_000;
    private static final int MAX_MESSAGES = 20;
    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Kuala_Lumpur");
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};
    private static final byte[] TRANSPARENT_PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="
    );
    private static final List<String> HEADERS = List.of(
            "eastapp_format",
            "languages",
            "sku_name",
            "tag_1",
            "tag_2",
            "unit",
            "minimum_balance",
            "current_balance",
            "maximum_balance",
            "recovery_percent",
            "minimum_price_rm",
            "maximum_price_rm",
            "supplier_names",
            "receivable_checklist",
            "stock_check_schedule",
            "stock_check_day",
            "stock_check_date",
            "active",
            "cooling_period"
    );

    private final TenantRepository tenantRepository;
    private final UserAccountRepository userAccountRepository;
    private final StockTagRepository tagRepository;
    private final StockSupplierRepository supplierRepository;
    private final StockSkuRepository skuRepository;
    private final StockMediaRepository mediaRepository;
    private final StockSkuCsvRequestRepository requestRepository;
    private final StockSkuExportSnapshotRepository snapshotRepository;
    private final WorkflowActivityService workflowActivityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public StockSkuCsvService(
            TenantRepository tenantRepository,
            UserAccountRepository userAccountRepository,
            StockTagRepository tagRepository,
            StockSupplierRepository supplierRepository,
            StockSkuRepository skuRepository,
            StockMediaRepository mediaRepository,
            StockSkuCsvRequestRepository requestRepository,
            StockSkuExportSnapshotRepository snapshotRepository,
            WorkflowActivityService workflowActivityService
    ) {
        this.tenantRepository = tenantRepository;
        this.userAccountRepository = userAccountRepository;
        this.tagRepository = tagRepository;
        this.supplierRepository = supplierRepository;
        this.skuRepository = skuRepository;
        this.mediaRepository = mediaRepository;
        this.requestRepository = requestRepository;
        this.snapshotRepository = snapshotRepository;
        this.workflowActivityService = workflowActivityService;
    }

    @Transactional
    public StockSkuCsvRequestResponse requestExport(AuthenticatedUser principal) {
        requireSubmitter(principal);
        ensureNoPending(principal.tenantId(), StockSkuCsvOperation.EXPORT);
        GeneratedCsv export = buildExport(principal.tenantId());
        StockSkuCsvRequest request = requestRepository.saveAndFlush(
                new StockSkuCsvRequest(
                        principal.tenantId(),
                        StockSkuCsvOperation.EXPORT,
                        export.fileName(),
                        new String(export.bytes(), StandardCharsets.UTF_8),
                        export.rowCount(),
                        export.rowCount(),
                        0,
                        0,
                        0,
                        principal.userId()
                )
        );
        recordSubmitted(principal, request);
        return requestResponse(principal.tenantId(), request);
    }

    @Transactional(readOnly = true)
    public CsvExport approvedExport(AuthenticatedUser principal) {
        requireSubmitter(principal);
        StockSkuExportSnapshot snapshot = snapshotRepository
                .findById(principal.tenantId())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "SKU_CSV_APPROVED_EXPORT_NOT_FOUND",
                        "No approved SKU export is available yet."
                ));
        return new CsvExport(
                snapshot.getFileName(),
                snapshot.getCsvContent().getBytes(StandardCharsets.UTF_8)
        );
    }

    private GeneratedCsv buildExport(UUID tenantId) {
        List<StockSku> skus = skuRepository.findAllByTenant_IdOrderByNameAsc(
                tenantId
        );
        try {
            StringWriter writer = new StringWriter();
            writer.write('\ufeff');
            CSVFormat format = CSVFormat.RFC4180.builder()
                    .setHeader(HEADERS.toArray(String[]::new))
                    .setRecordSeparator("\r\n")
                    .get();
            try (CSVPrinter printer = new CSVPrinter(writer, format)) {
                for (StockSku sku : skus) {
                    List<String> supplierNames = sku.getSuppliers().stream()
                            .map(StockSupplier::getSupplierName)
                            .sorted(String.CASE_INSENSITIVE_ORDER)
                            .toList();
                    printer.printRecord(
                            FORMAT_NAME,
                            LANGUAGES,
                            sku.getName(),
                            sku.getTag1() == null ? "" : sku.getTag1().getTag(),
                            sku.getTag2() == null ? "" : sku.getTag2().getTag(),
                            sku.getUnit(),
                            decimal(sku.getMinimumBalanceValue()),
                            decimal(sku.getCurrentBalanceValue()),
                            decimal(sku.getMaximumBalanceValue()),
                            sku.getRecoveryPercent(),
                            decimal(sku.getMinimumPriceRm()),
                            decimal(sku.getMaximumPriceRm()),
                            objectMapper.writeValueAsString(supplierNames),
                            objectMapper.writeValueAsString(sku.getReceivableChecklist()),
                            sku.getStockCheckSchedule().name(),
                            sku.getStockCheckDay() == null ? "" : sku.getStockCheckDay(),
                            sku.getStockCheckDate() == null ? "" : sku.getStockCheckDate(),
                            sku.isActive(),
                            sku.isCoolingPeriod()
                    );
                }
            }
            String fileName = "eastapp-skus-"
                    + LocalDate.now(ZONE_ID)
                    + ".csv";
            return new GeneratedCsv(
                    fileName,
                    writer.toString().getBytes(StandardCharsets.UTF_8),
                    skus.size()
            );
        } catch (IOException exception) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "SKU_CSV_EXPORT_FAILED",
                    "The SKU CSV could not be generated."
            );
        }
    }

    @Transactional(readOnly = true)
    public StockSkuCsvPreviewResponse preview(
            AuthenticatedUser principal,
            MultipartFile file
    ) {
        requireSubmitter(principal);
        Analysis analysis = analyse(principal.tenantId(), readCsv(file));
        return analysis.preview();
    }

    @Transactional
    public StockSkuCsvRequestResponse requestImport(
            AuthenticatedUser principal,
            MultipartFile file
    ) {
        requireSubmitter(principal);
        ensureNoPending(principal.tenantId(), StockSkuCsvOperation.IMPORT);
        String csv = readCsv(file);
        Analysis analysis = analyse(principal.tenantId(), csv);
        if (analysis.invalidRows() > 0) {
            throw badRequest(
                    "SKU_CSV_INVALID_ROWS",
                    "Fix every invalid CSV row before importing."
            );
        }
        if (analysis.readyRows().isEmpty()) {
            throw badRequest(
                    "SKU_CSV_NO_IMPORTABLE_ROWS",
                    "The SKU CSV contains no new rows to submit."
            );
        }
        StockSkuCsvRequest request = requestRepository.saveAndFlush(
                new StockSkuCsvRequest(
                        principal.tenantId(),
                        StockSkuCsvOperation.IMPORT,
                        sourceFileName(file),
                        csv,
                        analysis.totalRows(),
                        analysis.readyRows().size(),
                        analysis.duplicateRows(),
                        analysis.newTagCount(),
                        analysis.unmatchedSupplierNames().size(),
                        principal.userId()
                )
        );
        recordSubmitted(principal, request);
        return requestResponse(principal.tenantId(), request);
    }

    @Transactional(readOnly = true)
    public List<StockSkuCsvRequestResponse> listRequests(AuthenticatedUser principal) {
        requireSubmitter(principal);
        List<StockSkuCsvRequest> requests = requestRepository
                .findAllByTenantIdOrderByUpdatedAtDesc(principal.tenantId());
        Map<UUID, String> names = userNames(principal.tenantId(), requests);
        return requests.stream()
                .map(request -> requestResponse(request, names))
                .toList();
    }

    @Transactional
    public StockSkuCsvRequestResponse review(
            AuthenticatedUser principal,
            UUID requestId,
            ReviewStockSkuCsvRequest review
    ) {
        requireOwner(principal);
        StockSkuCsvRequest request = requestRepository
                .findLockedByIdAndTenantId(requestId, principal.tenantId())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "SKU_CSV_REQUEST_NOT_FOUND",
                        "SKU CSV request not found."
                ));
        if (request.getStatus() != StockSkuCsvRequestStatus.SUBMITTED) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "SKU_CSV_REQUEST_ALREADY_REVIEWED",
                    "This SKU CSV request has already been reviewed."
            );
        }
        StockSkuCsvRequestStatus decision = review.status();
        if (decision == StockSkuCsvRequestStatus.APPROVED) {
            if (request.getOperation() == StockSkuCsvOperation.IMPORT) {
                applyImport(request);
            } else {
                approveExport(principal, request);
            }
            request.approve(principal.userId(), review.note());
        } else if (decision == StockSkuCsvRequestStatus.REJECTED) {
            if (review.note() == null || review.note().isBlank()) {
                throw badRequest(
                        "SKU_CSV_REJECTION_REASON_REQUIRED",
                        "A reason is required when rejecting a SKU CSV request."
                );
            }
            request.reject(principal.userId(), review.note());
        } else {
            throw badRequest(
                    "SKU_CSV_REVIEW_DECISION_INVALID",
                    "CSV request status must be APPROVED or REJECTED."
            );
        }
        workflowActivityService.recordTransition(
                principal,
                "Stock",
                "SKU CSV " + request.getOperation().name().toLowerCase(Locale.ROOT),
                request.getId(),
                request.getFileName(),
                StockSkuCsvRequestStatus.SUBMITTED,
                request.getStatus(),
                "/api/v1/stock/sku-csv-requests/" + request.getId()
        );
        return requestResponse(principal.tenantId(), request);
    }

    private void applyImport(StockSkuCsvRequest request) {
        Analysis analysis = analyse(request.getTenantId(), request.getCsvContent());
        if (analysis.invalidRows() > 0
                || analysis.readyRows().size() != request.getReadyRows()
                || analysis.duplicateRows() != request.getDuplicateRows()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "SKU_CSV_IMPORT_CHANGED",
                    "SKU data changed after submission. Reject this request and submit a new CSV."
            );
        }

        Tenant tenant = tenant(request.getTenantId());
        UserAccount actor = userAccountRepository
                .findByIdAndTenant_Id(request.getRequestedByUserId(), request.getTenantId())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "USER_NOT_FOUND",
                        "Requesting user not found."
                ));
        StockMedia noImage = mediaRepository.save(new StockMedia(
                tenant,
                StockMedia.SKU_IMPORT_PLACEHOLDER_PREFIX + UUID.randomUUID(),
                "image/png",
                TRANSPARENT_PNG
        ));
        Map<String, StockTag> tagsByName = new LinkedHashMap<>();
        tagRepository.findAllByTenant_IdOrderByTagAsc(request.getTenantId())
                .forEach(tag -> tagsByName.put(normalise(tag.getTag()), tag));
        Map<String, StockSupplier> suppliersByName = new LinkedHashMap<>();
        supplierRepository.findAllByTenant_IdOrderBySupplierNameAsc(request.getTenantId())
                .forEach(supplier -> suppliersByName.put(
                        normalise(supplier.getSupplierName()), supplier
                ));

        for (ParsedSku row : analysis.readyRows()) {
            StockTag tag1 = null;
            if (!row.tag1().isBlank()) {
                tag1 = tagsByName.get(normalise(row.tag1()));
                if (tag1 == null) {
                    tag1 = tagRepository.saveAndFlush(new StockTag(tenant, row.tag1(), actor));
                    tagsByName.put(normalise(row.tag1()), tag1);
                }
            }

            StockTag tag2 = null;
            if (!row.tag2().isBlank()) {
                tag2 = tagsByName.get(normalise(row.tag2()));
                if (tag2 == null) {
                    tag2 = tagRepository.saveAndFlush(new StockTag(tenant, row.tag2(), actor));
                    tagsByName.put(normalise(row.tag2()), tag2);
                }
            }

            Set<StockSupplier> suppliers = new LinkedHashSet<>();
            for (String supplierName : row.supplierNames()) {
                StockSupplier supplier = suppliersByName.get(normalise(supplierName));
                if (supplier != null) suppliers.add(supplier);
            }

            skuRepository.save(new StockSku(
                    tenant,
                    row.name(),
                    tag1,
                    tag2,
                    row.unit(),
                    row.minimumBalance(),
                    row.maximumBalance(),
                    row.currentBalance(),
                    row.recoveryPercent(),
                    row.minimumPrice(),
                    row.maximumPrice(),
                    suppliers,
                    noImage,
                    List.of(),
                    row.receivableChecklist(),
                    row.stockCheckSchedule(),
                    row.stockCheckDay(),
                    row.stockCheckDate(),
                    row.active(),
                    row.coolingPeriod(),
                    actor
            ));
        }
        skuRepository.flush();
    }

    private void approveExport(
            AuthenticatedUser principal,
            StockSkuCsvRequest request
    ) {
        StockSkuExportSnapshot snapshot = snapshotRepository
                .findById(principal.tenantId())
                .orElse(null);
        if (snapshot == null) {
            snapshot = new StockSkuExportSnapshot(
                    principal.tenantId(),
                    request.getFileName(),
                    request.getCsvContent(),
                    request.getId(),
                    principal.userId()
            );
        } else {
            snapshot.overwrite(
                    request.getFileName(),
                    request.getCsvContent(),
                    request.getId(),
                    principal.userId()
            );
        }
        snapshotRepository.save(snapshot);
    }

    private Analysis analyse(UUID tenantId, String csv) {
        Set<String> existingSkuNames = new HashSet<>();
        skuRepository.findAllByTenant_IdOrderByNameAsc(tenantId)
                .forEach(sku -> existingSkuNames.add(normalise(sku.getName())));
        Set<String> existingTagNames = new HashSet<>();
        tagRepository.findAllByTenant_IdOrderByTagAsc(tenantId)
                .forEach(tag -> existingTagNames.add(normalise(tag.getTag())));
        Set<String> existingSupplierNames = new HashSet<>();
        supplierRepository.findAllByTenant_IdOrderBySupplierNameAsc(tenantId)
                .forEach(supplier -> existingSupplierNames.add(
                        normalise(supplier.getSupplierName())
                ));

        List<ParsedSku> readyRows = new ArrayList<>();
        Set<String> namesInFile = new HashSet<>();
        Set<String> newTags = new LinkedHashSet<>();
        Set<String> unmatchedSuppliers = new LinkedHashSet<>();
        List<String> errors = new ArrayList<>();
        int totalRows = 0;
        int duplicateRows = 0;
        int invalidRows = 0;

        CSVFormat format = CSVFormat.RFC4180.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true)
                .get();
        try (CSVParser parser = CSVParser.parse(csv, format)) {
            validateHeaders(parser.getHeaderNames());
            for (CSVRecord record : parser) {
                totalRows += 1;
                if (totalRows > MAX_ROWS) {
                    throw badRequest(
                            "SKU_CSV_TOO_MANY_ROWS",
                            "A SKU CSV may contain no more than " + MAX_ROWS + " rows."
                    );
                }
                try {
                    ParsedSku row = parseRow(record);
                    String normalisedName = normalise(row.name());
                    if (!namesInFile.add(normalisedName)
                            || existingSkuNames.contains(normalisedName)) {
                        duplicateRows += 1;
                        continue;
                    }
                    readyRows.add(row);
                    if (!row.tag1().isBlank()
                            && !existingTagNames.contains(normalise(row.tag1()))) {
                        newTags.add(normalise(row.tag1()));
                    }
                    if (!row.tag2().isBlank()
                            && !existingTagNames.contains(normalise(row.tag2()))) {
                        newTags.add(normalise(row.tag2()));
                    }
                    for (String supplierName : row.supplierNames()) {
                        if (!existingSupplierNames.contains(normalise(supplierName))) {
                            unmatchedSuppliers.add(supplierName);
                        }
                    }
                } catch (RowValidationException exception) {
                    invalidRows += 1;
                    if (errors.size() < MAX_MESSAGES) {
                        errors.add("Row " + (record.getRecordNumber() + 1)
                                + ": " + exception.getMessage());
                    }
                }
            }
        } catch (ApiException exception) {
            throw exception;
        } catch (IOException | UncheckedIOException | IllegalArgumentException exception) {
            throw badRequest(
                    "SKU_CSV_MALFORMED",
                    "The selected file is not a valid EastApp SKU CSV."
            );
        }

        if (totalRows == 0) {
            throw badRequest("SKU_CSV_EMPTY", "The SKU CSV contains no data rows.");
        }
        return new Analysis(
                totalRows,
                List.copyOf(readyRows),
                duplicateRows,
                invalidRows,
                newTags.size(),
                List.copyOf(unmatchedSuppliers),
                List.copyOf(errors)
        );
    }

    private ParsedSku parseRow(CSVRecord record) {
        if (!FORMAT_NAME.equals(text(record, "eastapp_format"))) {
            throw invalid("eastapp_format must be " + FORMAT_NAME + ".");
        }
        if (!LANGUAGES.equals(text(record, "languages"))) {
            throw invalid("languages must be " + LANGUAGES + ".");
        }
        String name = requiredText(record, "sku_name", 120);
        String tag1 = optionalText(record, "tag_1", 80);
        String tag2 = optionalText(record, "tag_2", 80);
        String unit = requiredText(record, "unit", 32);
        BigDecimal minimumBalance = decimal(record, "minimum_balance");
        BigDecimal currentBalance = decimal(record, "current_balance");
        BigDecimal maximumBalance = decimal(record, "maximum_balance");
        if (maximumBalance.compareTo(minimumBalance) < 0) {
            throw invalid("maximum_balance must be at least minimum_balance.");
        }
        int recoveryPercent = integer(record, "recovery_percent", 1, 100);
        BigDecimal minimumPrice = decimal(record, "minimum_price_rm");
        BigDecimal maximumPrice = decimal(record, "maximum_price_rm");
        if (maximumPrice.compareTo(minimumPrice) < 0) {
            throw invalid("maximum_price_rm must be at least minimum_price_rm.");
        }
        List<String> supplierNames = stringList(
                record, "supplier_names", 50, 120
        );
        List<String> checklist = stringList(
                record, "receivable_checklist", 50, 300
        );
        StockCheckSchedule stockCheckSchedule;
        try {
            stockCheckSchedule = StockCheckSchedule.valueOf(
                    requiredText(record, "stock_check_schedule", 16).toUpperCase(Locale.ROOT)
            );
        } catch (IllegalArgumentException exception) {
            throw invalid("stock_check_schedule must be AD_HOC, DAILY, WEEKLY or MONTHLY.");
        }
        String dayText = text(record, "stock_check_day");
        String dateText = text(record, "stock_check_date");
        Integer stockCheckDay = null;
        LocalDate stockCheckDate = null;
        switch (stockCheckSchedule) {
            case AD_HOC -> {
                if (!dayText.isEmpty()) {
                    throw invalid("stock_check_day must be blank for AD_HOC.");
                }
                stockCheckDate = localDate(record, "stock_check_date");
            }
            case DAILY -> {
                if (!dayText.isEmpty() || !dateText.isEmpty()) {
                    throw invalid("stock_check_day and stock_check_date must be blank for DAILY.");
                }
            }
            case WEEKLY -> {
                stockCheckDay = integer(record, "stock_check_day", 1, 7);
                if (!dateText.isEmpty()) {
                    throw invalid("stock_check_date must be blank for WEEKLY.");
                }
            }
            case MONTHLY -> {
                if (!dayText.isEmpty()) {
                    stockCheckDay = integer(record, "stock_check_day", 1, 28);
                }
                if (!dateText.isEmpty()) {
                    throw invalid("stock_check_date must be blank for MONTHLY.");
                }
            }
        }
        return new ParsedSku(
                name,
                tag1,
                tag2,
                unit,
                minimumBalance,
                currentBalance,
                maximumBalance,
                recoveryPercent,
                minimumPrice,
                maximumPrice,
                supplierNames,
                checklist,
                stockCheckSchedule,
                stockCheckDay,
                stockCheckDate,
                bool(record, "active"),
                bool(record, "cooling_period")
        );
    }

    private List<String> stringList(
            CSVRecord record,
            String header,
            int maxItems,
            int maxLength
    ) {
        String value = text(record, header);
        if (value.isEmpty()) return List.of();
        try {
            List<String> raw = objectMapper.readValue(value, STRING_LIST);
            if (raw == null) {
                throw invalid(header + " must be a JSON text list.");
            }
            if (raw.size() > maxItems) {
                throw invalid(header + " may contain no more than " + maxItems + " items.");
            }
            List<String> result = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (String item : raw) {
                String resolved = item == null ? "" : item.trim();
                if (resolved.isEmpty()) continue;
                if (resolved.length() > maxLength) {
                    throw invalid(header + " contains an item that is too long.");
                }
                if (seen.add(normalise(resolved))) result.add(resolved);
            }
            return List.copyOf(result);
        } catch (RowValidationException exception) {
            throw exception;
        } catch (IOException exception) {
            throw invalid(header + " must be a JSON text list, for example [\"Item 1\"].");
        }
    }

    private String readCsv(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw badRequest("SKU_CSV_REQUIRED", "Select a CSV file first.");
        }
        String fileName = file.getOriginalFilename();
        if (fileName == null || !fileName.toLowerCase(Locale.ROOT).endsWith(".csv")) {
            throw badRequest("SKU_CSV_REQUIRED", "Only .csv files are supported.");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw badRequest("SKU_CSV_TOO_LARGE", "The SKU CSV must not exceed 2 MB.");
        }
        try {
            String value = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(file.getBytes()))
                    .toString();
            if (!value.isEmpty() && value.charAt(0) == '\ufeff') {
                value = value.substring(1);
            }
            if (value.indexOf('\u0000') >= 0) {
                throw badRequest(
                        "SKU_CSV_ENCODING_INVALID",
                        "The SKU CSV must use UTF-8 encoding."
                );
            }
            return value;
        } catch (CharacterCodingException exception) {
            throw badRequest(
                    "SKU_CSV_ENCODING_INVALID",
                    "The SKU CSV must use UTF-8 encoding."
            );
        } catch (IOException exception) {
            throw badRequest("SKU_CSV_READ_FAILED", "The selected CSV could not be read.");
        }
    }

    private static void validateHeaders(List<String> headers) {
        if (headers.size() != new LinkedHashSet<>(headers).size()
                || !headers.containsAll(HEADERS)) {
            throw badRequest(
                    "SKU_CSV_FORMAT_NOT_RECOGNISED",
                    "The selected file is not a recognised EastApp SKU CSV."
            );
        }
    }

    private static String requiredText(CSVRecord record, String header, int maxLength) {
        String value = text(record, header);
        if (value.isEmpty()) throw invalid(header + " is required.");
        if (value.length() > maxLength) throw invalid(header + " is too long.");
        return value;
    }

    private static String optionalText(CSVRecord record, String header, int maxLength) {
        String value = text(record, header);
        if (value.length() > maxLength) throw invalid(header + " is too long.");
        return value;
    }

    private static String text(CSVRecord record, String header) {
        String value = record.get(header);
        return value == null ? "" : value.trim();
    }

    private static BigDecimal decimal(CSVRecord record, String header) {
        try {
            BigDecimal value = new BigDecimal(requiredText(record, header, 40));
            if (value.signum() < 0) throw invalid(header + " must not be negative.");
            return value;
        } catch (NumberFormatException exception) {
            throw invalid(header + " must be a valid number.");
        }
    }

    private static int integer(CSVRecord record, String header, int min, int max) {
        try {
            int value = new BigDecimal(requiredText(record, header, 20)).intValueExact();
            if (value < min || value > max) {
                throw invalid(header + " must be between " + min + " and " + max + ".");
            }
            return value;
        } catch (ArithmeticException | NumberFormatException exception) {
            throw invalid(header + " must be a whole number.");
        }
    }

    private static LocalDate localDate(CSVRecord record, String header) {
        try {
            return LocalDate.parse(requiredText(record, header, 10));
        } catch (DateTimeParseException exception) {
            throw invalid(header + " must use YYYY-MM-DD format.");
        }
    }

    private static boolean bool(CSVRecord record, String header) {
        String value = requiredText(record, header, 5);
        if ("true".equalsIgnoreCase(value)) return true;
        if ("false".equalsIgnoreCase(value)) return false;
        throw invalid(header + " must be true or false.");
    }

    private static String decimal(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static String normalise(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private void requireSubmitter(AuthenticatedUser principal) {
        if (!principal.isHead()) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "SKU_CSV_SUBMITTER_REQUIRED",
                    "Only Admin, Head or Owner users may submit SKU CSV requests."
            );
        }
    }

    private void ensureNoPending(UUID tenantId, StockSkuCsvOperation operation) {
        if (requestRepository.existsByTenantIdAndOperationAndStatus(
                tenantId,
                operation,
                StockSkuCsvRequestStatus.SUBMITTED
        )) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "SKU_CSV_REQUEST_ALREADY_PENDING",
                    "A " + operation.name().toLowerCase(Locale.ROOT)
                            + " request is already waiting for approval."
            );
        }
    }

    private static String sourceFileName(MultipartFile file) {
        String name = file.getOriginalFilename();
        if (name == null || name.isBlank()) return "eastapp-skus-import.csv";
        String clean = name.replace('\\', '/');
        int slash = clean.lastIndexOf('/');
        clean = slash >= 0 ? clean.substring(slash + 1) : clean;
        return clean.length() <= 255 ? clean : clean.substring(clean.length() - 255);
    }

    private void recordSubmitted(
            AuthenticatedUser principal,
            StockSkuCsvRequest request
    ) {
        workflowActivityService.recordTransition(
                principal,
                "Stock",
                "SKU CSV " + request.getOperation().name().toLowerCase(Locale.ROOT),
                request.getId(),
                request.getFileName(),
                null,
                StockSkuCsvRequestStatus.SUBMITTED,
                "/api/v1/stock/sku-csv-requests/" + request.getId()
        );
    }

    private StockSkuCsvRequestResponse requestResponse(
            UUID tenantId,
            StockSkuCsvRequest request
    ) {
        return requestResponse(request, userNames(tenantId, List.of(request)));
    }

    private Map<UUID, String> userNames(
            UUID tenantId,
            List<StockSkuCsvRequest> requests
    ) {
        Set<UUID> userIds = new LinkedHashSet<>();
        requests.forEach(request -> {
            userIds.add(request.getRequestedByUserId());
            if (request.getReviewedByUserId() != null) {
                userIds.add(request.getReviewedByUserId());
            }
        });
        Map<UUID, String> names = new LinkedHashMap<>();
        if (!userIds.isEmpty()) {
            userAccountRepository.findAllByTenant_IdAndIdIn(tenantId, userIds)
                    .forEach(user -> names.put(user.getId(), user.getFullName()));
        }
        return names;
    }

    private static StockSkuCsvRequestResponse requestResponse(
            StockSkuCsvRequest request,
            Map<UUID, String> names
    ) {
        return new StockSkuCsvRequestResponse(
                request.getId(),
                request.getOperation(),
                request.getStatus(),
                request.getFileName(),
                request.getTotalRows(),
                request.getReadyRows(),
                request.getDuplicateRows(),
                request.getNewTagCount(),
                request.getUnmatchedSupplierCount(),
                request.getRequestedByUserId(),
                names.getOrDefault(request.getRequestedByUserId(), "Unknown user"),
                request.getSubmittedAt(),
                request.getReviewedByUserId(),
                request.getReviewedByUserId() == null
                        ? null
                        : names.getOrDefault(request.getReviewedByUserId(), "Unknown user"),
                request.getReviewedAt(),
                request.getReviewNote()
        );
    }

    private void requireOwner(AuthenticatedUser principal) {
        if (!principal.isOwner()) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "OWNER_REQUIRED",
                    "Only Admin or Owner users may approve or reject SKU CSV requests."
            );
        }
    }

    private Tenant tenant(UUID tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "TENANT_NOT_FOUND",
                        "Tenant not found."
                ));
    }

    private static RowValidationException invalid(String message) {
        return new RowValidationException(message);
    }

    private static ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    public record CsvExport(String fileName, byte[] bytes) {}

    private record GeneratedCsv(String fileName, byte[] bytes, int rowCount) {}

    private record ParsedSku(
            String name,
            String tag1,
            String tag2,
            String unit,
            BigDecimal minimumBalance,
            BigDecimal currentBalance,
            BigDecimal maximumBalance,
            int recoveryPercent,
            BigDecimal minimumPrice,
            BigDecimal maximumPrice,
            List<String> supplierNames,
            List<String> receivableChecklist,
            StockCheckSchedule stockCheckSchedule,
            Integer stockCheckDay,
            LocalDate stockCheckDate,
            boolean active,
            boolean coolingPeriod
    ) {}

    private record Analysis(
            int totalRows,
            List<ParsedSku> readyRows,
            int duplicateRows,
            int invalidRows,
            int newTagCount,
            List<String> unmatchedSupplierNames,
            List<String> errors
    ) {
        StockSkuCsvPreviewResponse preview() {
            return new StockSkuCsvPreviewResponse(
                    FORMAT_NAME,
                    totalRows,
                    readyRows.size(),
                    duplicateRows,
                    invalidRows,
                    newTagCount,
                    unmatchedSupplierNames.size(),
                    unmatchedSupplierNames.stream().limit(MAX_MESSAGES).toList(),
                    errors
            );
        }
    }

    private static final class RowValidationException extends RuntimeException {
        private RowValidationException(String message) {
            super(message);
        }
    }
}
