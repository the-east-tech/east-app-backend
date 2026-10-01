package com.eastapp.backend.stock.service;

import com.eastapp.backend.auth.security.AuthenticatedUser;
import com.eastapp.backend.common.csv.CsvSupport;
import com.eastapp.backend.common.error.ApiException;
import com.eastapp.backend.organisation.Tenant;
import com.eastapp.backend.organisation.TenantRepository;
import com.eastapp.backend.people.UserAccount;
import com.eastapp.backend.people.UserAccountRepository;
import com.eastapp.backend.stock.StockCheckSchedule;
import com.eastapp.backend.stock.StockMedia;
import com.eastapp.backend.stock.StockMediaRepository;
import com.eastapp.backend.stock.StockSku;
import com.eastapp.backend.stock.StockSkuRepository;
import com.eastapp.backend.stock.StockSupplier;
import com.eastapp.backend.stock.StockSupplierRepository;
import com.eastapp.backend.stock.StockTag;
import com.eastapp.backend.stock.StockTagRepository;
import com.eastapp.backend.stock.api.StockSkuCsvImportResponse;
import com.eastapp.backend.stock.api.StockSkuCsvPreviewResponse;
import com.eastapp.backend.stock.api.UpsertStockSkuRequest;
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
    private static final int MAX_FILE_BYTES = 2 * 1024 * 1024;
    private static final int MAX_ROWS = 1_000;
    private static final int MAX_MESSAGES = 20;
    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Kuala_Lumpur");
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};
    private static final TypeReference<List<Integer>> INTEGER_LIST = new TypeReference<>() {};
    private static final byte[] TRANSPARENT_PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="
    );
    private static final List<String> HEADERS = List.of(
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
            "cooling_period",
            "stock_check_day_2",
            "stock_check_days"
    );

    private final TenantRepository tenantRepository;
    private final UserAccountRepository userAccountRepository;
    private final StockTagRepository tagRepository;
    private final StockSupplierRepository supplierRepository;
    private final StockSkuRepository skuRepository;
    private final StockMediaRepository mediaRepository;
    private final StockService stockService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public StockSkuCsvService(
            TenantRepository tenantRepository,
            UserAccountRepository userAccountRepository,
            StockTagRepository tagRepository,
            StockSupplierRepository supplierRepository,
            StockSkuRepository skuRepository,
            StockMediaRepository mediaRepository,
            StockService stockService
    ) {
        this.tenantRepository = tenantRepository;
        this.userAccountRepository = userAccountRepository;
        this.tagRepository = tagRepository;
        this.supplierRepository = supplierRepository;
        this.skuRepository = skuRepository;
        this.mediaRepository = mediaRepository;
        this.stockService = stockService;
    }

    @Transactional(readOnly = true)
    public CsvExport exportSkus(AuthenticatedUser principal) {
        requireOwner(principal);
        List<StockSku> skus = skuRepository.findAllByTenant_IdOrderByNameAsc(
                principal.tenantId()
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
                            sku.isCoolingPeriod(),
                            sku.getStockCheckDay2() == null ? "" : sku.getStockCheckDay2(),
                            sku.getStockCheckSchedule() == StockCheckSchedule.WEEKLY
                                    ? objectMapper.writeValueAsString(sku.getStockCheckDays()) : ""
                    );
                }
            }
            String fileName = CsvSupport.exportFileName(
                    principal.tenantCode(), "skus", LocalDate.now(ZONE_ID));
            return new CsvExport(
                    fileName,
                    writer.toString().getBytes(StandardCharsets.UTF_8)
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
        requireOwner(principal);
        Analysis analysis = analyse(principal.tenantId(), file);
        return analysis.preview();
    }

    @Transactional
    public StockSkuCsvImportResponse importSkus(
            AuthenticatedUser principal,
            MultipartFile file
    ) {
        requireOwner(principal);
        Analysis analysis = analyse(principal.tenantId(), file);
        if (analysis.invalidRows() > 0) {
            throw badRequest(
                    "SKU_CSV_INVALID_ROWS",
                    "Fix every invalid CSV row before importing."
            );
        }
        if (analysis.readyRows().isEmpty()) {
            return new StockSkuCsvImportResponse(
                    0,
                    analysis.duplicateRows(),
                    0,
                    0
            );
        }

        Tenant tenant = tenant(principal.tenantId());
        UserAccount actor = actor(principal);
        Map<String, StockSku> existingSkus = new LinkedHashMap<>();
        skuRepository.findAllByTenant_IdOrderByNameAsc(principal.tenantId())
                .forEach(sku -> existingSkus.put(normalise(sku.getName()), sku));
        boolean createsNewSku = analysis.readyRows().stream()
                .anyMatch(row -> !existingSkus.containsKey(normalise(row.name())));
        StockMedia noImage = createsNewSku
                ? mediaRepository.save(new StockMedia(
                        tenant,
                        StockMedia.SKU_IMPORT_PLACEHOLDER_PREFIX + UUID.randomUUID(),
                        "image/png",
                        TRANSPARENT_PNG
                )) : null;
        Map<String, StockTag> tagsByName = new LinkedHashMap<>();
        tagRepository.findAllByTenant_IdOrderByTagAsc(principal.tenantId())
                .forEach(tag -> tagsByName.put(normalise(tag.getTag()), tag));
        Map<String, StockSupplier> suppliersByName = new LinkedHashMap<>();
        supplierRepository.findAllByTenant_IdOrderBySupplierNameAsc(principal.tenantId())
                .forEach(supplier -> suppliersByName.put(
                        normalise(supplier.getSupplierName()), supplier
                ));

        int createdTags = 0;
        int importedRows = 0;
        int unmatchedSupplierLinks = 0;
        for (ParsedSku row : analysis.readyRows()) {
            StockTag tag1 = null;
            if (!row.tag1().isBlank()) {
                tag1 = tagsByName.get(normalise(row.tag1()));
                if (tag1 == null) {
                    tag1 = tagRepository.saveAndFlush(new StockTag(tenant, row.tag1(), actor));
                    tagsByName.put(normalise(row.tag1()), tag1);
                    createdTags += 1;
                }
            }

            StockTag tag2 = null;
            if (!row.tag2().isBlank()) {
                tag2 = tagsByName.get(normalise(row.tag2()));
                if (tag2 == null) {
                    tag2 = tagRepository.saveAndFlush(new StockTag(tenant, row.tag2(), actor));
                    tagsByName.put(normalise(row.tag2()), tag2);
                    createdTags += 1;
                }
            }

            Set<StockSupplier> suppliers = new LinkedHashSet<>();
            for (String supplierName : row.supplierNames()) {
                StockSupplier supplier = suppliersByName.get(normalise(supplierName));
                if (supplier == null) {
                    unmatchedSupplierLinks += 1;
                } else {
                    suppliers.add(supplier);
                }
            }

            StockSku existing = existingSkus.get(normalise(row.name()));
            UpsertStockSkuRequest proposed = new UpsertStockSkuRequest(
                    row.name(),
                    tag1 == null ? null : tag1.getId(),
                    tag2 == null ? null : tag2.getId(),
                    row.unit(),
                    row.minimumBalance(),
                    row.maximumBalance(),
                    row.currentBalance(),
                    row.recoveryPercent(),
                    row.minimumPrice(),
                    row.maximumPrice(),
                    suppliers.stream().map(StockSupplier::getId).toList(),
                    existing == null ? noImage.getStorageKey() : null,
                    List.of(),
                    row.receivableChecklist(),
                    row.stockCheckSchedule(),
                    row.stockCheckDay(),
                    row.stockCheckDay2(),
                    row.stockCheckDays(),
                    row.stockCheckDate(),
                    row.active(),
                    row.coolingPeriod()
            );
            stockService.importSkuNow(principal,
                    existing == null ? null : existing.getId(), proposed);
            importedRows += 1;
        }

        return new StockSkuCsvImportResponse(
                importedRows,
                analysis.duplicateRows(),
                createdTags,
                unmatchedSupplierLinks
        );
    }

    private Analysis analyse(UUID tenantId, MultipartFile file) {
        String csv = readCsv(file);
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
                    if (!namesInFile.add(normalisedName)) {
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
                    "The selected file is not a valid SKU CSV."
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
        String name = requiredText(record, "sku_name", 120);
        String tag1 = optionalText(record, "tag_1", 80);
        String tag2 = optionalText(record, "tag_2", 80);
        String unit = requiredText(record, "unit", 32);
        BigDecimal minimumBalance = decimal(record, "minimum_balance");
        BigDecimal currentBalance = record.isMapped("current_balance")
                && !text(record, "current_balance").isEmpty()
                ? decimal(record, "current_balance") : BigDecimal.ZERO;
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
        Integer stockCheckDay2 = null;
        List<Integer> stockCheckDays = List.of();
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
                if (record.isMapped("stock_check_days")
                        && !text(record, "stock_check_days").isEmpty()) {
                    stockCheckDays = integerList(record, "stock_check_days");
                    stockCheckDay = stockCheckDays.get(0);
                    stockCheckDay2 = stockCheckDays.size() > 1 ? stockCheckDays.get(1) : null;
                } else {
                    stockCheckDay = integer(record, "stock_check_day", 1, 7);
                    if (record.isMapped("stock_check_day_2")
                            && !text(record, "stock_check_day_2").isEmpty()) {
                        stockCheckDay2 = integer(record, "stock_check_day_2", 1, 7);
                        if (stockCheckDay2.equals(stockCheckDay)) {
                            throw invalid("stock_check_day_2 must differ from stock_check_day.");
                        }
                    }
                    stockCheckDays = stockCheckDay2 == null
                            ? List.of(stockCheckDay) : List.of(stockCheckDay, stockCheckDay2);
                }
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
        if (stockCheckSchedule != StockCheckSchedule.WEEKLY
                && record.isMapped("stock_check_day_2")
                && !text(record, "stock_check_day_2").isEmpty()) {
            throw invalid("stock_check_day_2 is only valid for WEEKLY.");
        }
        if (stockCheckSchedule != StockCheckSchedule.WEEKLY
                && record.isMapped("stock_check_days")
                && !text(record, "stock_check_days").isEmpty()) {
            throw invalid("stock_check_days is only valid for WEEKLY.");
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
                stockCheckDay2,
                stockCheckDays,
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

    private List<Integer> integerList(CSVRecord record, String header) {
        try {
            List<Integer> raw = objectMapper.readValue(text(record, header), INTEGER_LIST);
            if (raw == null || raw.isEmpty() || raw.size() > 7
                    || raw.stream().anyMatch(day -> day == null || day < 1 || day > 7)
                    || raw.stream().distinct().count() != raw.size()) {
                throw invalid(header + " must contain between 1 and 7 unique weekdays.");
            }
            return raw.stream().sorted().toList();
        } catch (RowValidationException exception) {
            throw exception;
        } catch (IOException exception) {
            throw invalid(header + " must be a JSON number list, for example [1,3,5].");
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
        List<String> requiredHeaders = HEADERS.stream()
                .filter(name -> !name.equals("current_balance")
                        && !name.equals("stock_check_day_2")
                        && !name.equals("stock_check_days")).toList();
        if (headers.size() != new LinkedHashSet<>(headers).size()
                || !headers.containsAll(requiredHeaders)
                || !HEADERS.containsAll(headers)) {
            throw badRequest(
                    "SKU_CSV_FORMAT_NOT_RECOGNISED",
                    "The selected file is not a recognised SKU CSV."
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

    private void requireOwner(AuthenticatedUser principal) {
        if (!principal.isOwner()) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "OWNER_REQUIRED",
                    "Only Owner users may import or export SKU files."
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

    private UserAccount actor(AuthenticatedUser principal) {
        return userAccountRepository.findByIdAndTenant_Id(
                        principal.userId(), principal.tenantId()
                )
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "USER_NOT_FOUND",
                        "User not found."
                ));
    }

    private static RowValidationException invalid(String message) {
        return new RowValidationException(message);
    }

    private static ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    public record CsvExport(String fileName, byte[] bytes) {}

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
            Integer stockCheckDay2,
            List<Integer> stockCheckDays,
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
