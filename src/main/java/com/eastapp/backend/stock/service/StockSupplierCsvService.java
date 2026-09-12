package com.eastapp.backend.stock.service;

import com.eastapp.backend.auth.security.AuthenticatedUser;
import com.eastapp.backend.common.error.ApiException;
import com.eastapp.backend.stock.StockSupplier;
import com.eastapp.backend.stock.StockSupplierRepository;
import com.eastapp.backend.stock.api.CreateStockSupplierRequest;
import com.eastapp.backend.stock.api.StockSupplierCsvImportResponse;
import com.eastapp.backend.stock.api.StockSupplierCsvPreviewResponse;
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
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class StockSupplierCsvService {
    private static final String FORMAT_NAME = "EASTAPP_SUPPLIER_CSV";
    private static final int FORMAT_VERSION = 1;
    private static final String LANGUAGES = "ENGLISH|CHINESE";
    private static final int MAX_FILE_BYTES = 2 * 1024 * 1024;
    private static final int MAX_ROWS = 1_000;
    private static final int MAX_MESSAGES = 20;
    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Kuala_Lumpur");
    private static final List<String> HEADERS = List.of(
            "eastapp_format",
            "format_version",
            "languages",
            "supplier_name",
            "supplier_item",
            "contact_person",
            "phone",
            "address_1",
            "address_2",
            "website_or_google_link",
            "notes",
            "unit",
            "recommended_purchase_amount",
            "recommended_purchase_frequency",
            "pricing_per_unit",
            "minimum_balance",
            "maximum_balance"
    );

    private final StockSupplierRepository supplierRepository;
    private final StockService stockService;

    public StockSupplierCsvService(
            StockSupplierRepository supplierRepository,
            StockService stockService
    ) {
        this.supplierRepository = supplierRepository;
        this.stockService = stockService;
    }

    @Transactional(readOnly = true)
    public CsvExport exportSuppliers(AuthenticatedUser principal) {
        requireOwner(principal);
        List<StockSupplier> suppliers = supplierRepository
                .findAllByTenant_IdOrderBySupplierNameAsc(principal.tenantId());
        try {
            StringWriter writer = new StringWriter();
            writer.write('\ufeff');
            CSVFormat format = CSVFormat.RFC4180.builder()
                    .setHeader(HEADERS.toArray(String[]::new))
                    .setRecordSeparator("\r\n")
                    .get();
            try (CSVPrinter printer = new CSVPrinter(writer, format)) {
                for (StockSupplier supplier : suppliers) {
                    printer.printRecord(
                            FORMAT_NAME,
                            FORMAT_VERSION,
                            LANGUAGES,
                            supplier.getSupplierName(),
                            supplier.getSupplierItem(),
                            supplier.getContactPerson(),
                            supplier.getPhone(),
                            supplier.getAddress(),
                            supplier.getAddress2(),
                            supplier.getWebsiteOrGoogleLink(),
                            supplier.getNotes(),
                            supplier.getUnit(),
                            decimal(supplier.getRecommendedPurchaseAmount()),
                            supplier.getRecommendedPurchaseFrequency(),
                            decimal(supplier.getPricingPerUnit()),
                            decimal(supplier.getMinimumBalanceValue()),
                            decimal(supplier.getMaximumBalanceValue())
                    );
                }
            }
            return new CsvExport(
                    "eastapp-suppliers-" + LocalDate.now(ZONE_ID) + ".csv",
                    writer.toString().getBytes(StandardCharsets.UTF_8)
            );
        } catch (IOException exception) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "SUPPLIER_CSV_EXPORT_FAILED",
                    "The Supplier CSV could not be generated."
            );
        }
    }

    @Transactional(readOnly = true)
    public StockSupplierCsvPreviewResponse preview(
            AuthenticatedUser principal,
            MultipartFile file
    ) {
        requireOwner(principal);
        return analyse(principal.tenantId(), file).preview();
    }

    @Transactional
    public StockSupplierCsvImportResponse importSuppliers(
            AuthenticatedUser principal,
            MultipartFile file
    ) {
        requireOwner(principal);
        Analysis analysis = analyse(principal.tenantId(), file);
        if (analysis.invalidRows() > 0) {
            throw badRequest(
                    "SUPPLIER_CSV_INVALID_ROWS",
                    "Fix every invalid CSV row before importing."
            );
        }
        int importedRows = 0;
        for (ParsedSupplier row : analysis.readyRows()) {
            stockService.createSupplier(principal, new CreateStockSupplierRequest(
                    row.supplierName(),
                    row.supplierItem(),
                    row.contactPerson(),
                    row.phone(),
                    row.address1(),
                    row.address2(),
                    row.websiteOrGoogleLink(),
                    row.notes(),
                    row.unit(),
                    row.recommendedPurchaseAmount(),
                    row.recommendedPurchaseFrequency(),
                    row.pricingPerUnit(),
                    row.minimumBalance(),
                    row.maximumBalance(),
                    BigDecimal.ZERO
            ));
            importedRows += 1;
        }
        return new StockSupplierCsvImportResponse(
                importedRows,
                analysis.duplicateRows()
        );
    }

    private Analysis analyse(java.util.UUID tenantId, MultipartFile file) {
        String csv = readCsv(file);
        Set<String> existingNames = new HashSet<>();
        supplierRepository.findAllByTenant_IdOrderBySupplierNameAsc(tenantId)
                .forEach(supplier -> existingNames.add(normalise(supplier.getSupplierName())));

        List<ParsedSupplier> readyRows = new ArrayList<>();
        Set<String> namesInFile = new HashSet<>();
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
                            "SUPPLIER_CSV_TOO_MANY_ROWS",
                            "A Supplier CSV may contain no more than " + MAX_ROWS + " rows."
                    );
                }
                try {
                    ParsedSupplier row = parseRow(record);
                    String name = normalise(row.supplierName());
                    if (!namesInFile.add(name) || existingNames.contains(name)) {
                        duplicateRows += 1;
                        continue;
                    }
                    readyRows.add(row);
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
                    "SUPPLIER_CSV_MALFORMED",
                    "The selected file is not a valid EastApp Supplier CSV."
            );
        }
        if (totalRows == 0) {
            throw badRequest(
                    "SUPPLIER_CSV_EMPTY",
                    "The Supplier CSV contains no data rows."
            );
        }
        return new Analysis(
                totalRows,
                List.copyOf(readyRows),
                duplicateRows,
                invalidRows,
                List.copyOf(errors)
        );
    }

    private static ParsedSupplier parseRow(CSVRecord record) {
        if (!FORMAT_NAME.equals(text(record, "eastapp_format"))) {
            throw invalid("eastapp_format must be " + FORMAT_NAME + ".");
        }
        if (integer(record, "format_version") != FORMAT_VERSION) {
            throw invalid("Unsupported format_version.");
        }
        if (!LANGUAGES.equals(text(record, "languages"))) {
            throw invalid("languages must be " + LANGUAGES + ".");
        }
        BigDecimal minimumBalance = decimal(record, "minimum_balance");
        BigDecimal maximumBalance = decimal(record, "maximum_balance");
        if (maximumBalance.compareTo(minimumBalance) < 0) {
            throw invalid("maximum_balance must be at least minimum_balance.");
        }
        return new ParsedSupplier(
                requiredText(record, "supplier_name", 120),
                requiredText(record, "supplier_item", 160),
                optionalText(record, "contact_person", 120),
                optionalText(record, "phone", 32),
                optionalText(record, "address_1", 500),
                optionalText(record, "address_2", 500),
                optionalText(record, "website_or_google_link", 1000),
                optionalText(record, "notes", 1000),
                requiredText(record, "unit", 32),
                decimal(record, "recommended_purchase_amount"),
                optionalText(record, "recommended_purchase_frequency", 80),
                decimal(record, "pricing_per_unit"),
                minimumBalance,
                maximumBalance
        );
    }

    private static String readCsv(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw badRequest("SUPPLIER_CSV_REQUIRED", "Select a CSV file first.");
        }
        String fileName = file.getOriginalFilename();
        if (fileName == null || !fileName.toLowerCase(Locale.ROOT).endsWith(".csv")) {
            throw badRequest("SUPPLIER_CSV_REQUIRED", "Only .csv files are supported.");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw badRequest(
                    "SUPPLIER_CSV_TOO_LARGE",
                    "The Supplier CSV must not exceed 2 MB."
            );
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
                        "SUPPLIER_CSV_ENCODING_INVALID",
                        "The Supplier CSV must use UTF-8 encoding."
                );
            }
            return value;
        } catch (CharacterCodingException exception) {
            throw badRequest(
                    "SUPPLIER_CSV_ENCODING_INVALID",
                    "The Supplier CSV must use UTF-8 encoding."
            );
        } catch (IOException exception) {
            throw badRequest(
                    "SUPPLIER_CSV_READ_FAILED",
                    "The selected CSV could not be read."
            );
        }
    }

    private static void validateHeaders(List<String> headers) {
        if (headers.size() != new LinkedHashSet<>(headers).size()
                || !headers.containsAll(HEADERS)) {
            throw badRequest(
                    "SUPPLIER_CSV_FORMAT_NOT_RECOGNISED",
                    "The selected file is not a recognised EastApp Supplier CSV v"
                            + FORMAT_VERSION + "."
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

    private static int integer(CSVRecord record, String header) {
        try {
            return new BigDecimal(requiredText(record, header, 20)).intValueExact();
        } catch (ArithmeticException | NumberFormatException exception) {
            throw invalid(header + " must be a whole number.");
        }
    }

    private static String decimal(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static String normalise(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static void requireOwner(AuthenticatedUser principal) {
        if (!principal.isOwner()) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "OWNER_REQUIRED",
                    "Only Owner users may import or export Supplier files."
            );
        }
    }

    private static RowValidationException invalid(String message) {
        return new RowValidationException(message);
    }

    private static ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    public record CsvExport(String fileName, byte[] bytes) {}

    private record ParsedSupplier(
            String supplierName,
            String supplierItem,
            String contactPerson,
            String phone,
            String address1,
            String address2,
            String websiteOrGoogleLink,
            String notes,
            String unit,
            BigDecimal recommendedPurchaseAmount,
            String recommendedPurchaseFrequency,
            BigDecimal pricingPerUnit,
            BigDecimal minimumBalance,
            BigDecimal maximumBalance
    ) {}

    private record Analysis(
            int totalRows,
            List<ParsedSupplier> readyRows,
            int duplicateRows,
            int invalidRows,
            List<String> errors
    ) {
        StockSupplierCsvPreviewResponse preview() {
            return new StockSupplierCsvPreviewResponse(
                    FORMAT_NAME,
                    FORMAT_VERSION,
                    totalRows,
                    readyRows.size(),
                    duplicateRows,
                    invalidRows,
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
