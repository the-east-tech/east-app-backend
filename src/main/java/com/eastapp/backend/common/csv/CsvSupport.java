package com.eastapp.backend.common.csv;

import com.eastapp.backend.common.error.ApiException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class CsvSupport {
    public static final int MAX_ROWS = 1_000;
    public static final int MAX_MESSAGES = 20;
    private static final int MAX_FILE_BYTES = 2 * 1024 * 1024;

    private CsvSupport() {}

    public static List<CSVRecord> records(
            MultipartFile file,
            List<String> expectedHeaders,
            String formatName,
            int formatVersion
    ) {
        if (file == null || file.isEmpty()) {
            throw badRequest("CSV_FILE_REQUIRED", "Choose a non-empty CSV file.");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw badRequest("CSV_FILE_TOO_LARGE", "CSV files are limited to 2 MB.");
        }
        String csv = decode(file);
        if (csv.startsWith("\uFEFF")) csv = csv.substring(1);
        CSVFormat format = CSVFormat.RFC4180.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true)
                .get();
        try (CSVParser parser = CSVParser.parse(csv, format)) {
            if (!parser.getHeaderNames().equals(expectedHeaders)) {
                throw badRequest(
                        "CSV_HEADERS_INVALID",
                        "CSV headers do not match the EastApp template. Export a fresh template and try again."
                );
            }
            List<CSVRecord> records = parser.getRecords();
            if (records.size() > MAX_ROWS) {
                throw badRequest("CSV_TOO_MANY_ROWS", "CSV imports are limited to 1,000 rows.");
            }
            for (CSVRecord record : records) {
                if (!formatName.equals(record.get("eastapp_format").trim())) {
                    throw badRequest("CSV_FORMAT_INVALID", "This CSV belongs to a different EastApp import.");
                }
                if (!Integer.toString(formatVersion).equals(record.get("format_version").trim())) {
                    throw badRequest("CSV_VERSION_INVALID", "Export a fresh EastApp CSV template and try again.");
                }
            }
            return records;
        } catch (IOException | IllegalArgumentException exception) {
            throw badRequest("CSV_PARSE_FAILED", "The CSV file could not be read: " + exception.getMessage());
        }
    }

    public static String text(CSVRecord record, String column) {
        return record.get(column).trim();
    }

    public static boolean bool(CSVRecord record, String column) {
        String value = text(record, column);
        if (value.equalsIgnoreCase("true")) return true;
        if (value.equalsIgnoreCase("false")) return false;
        throw new IllegalArgumentException(column + " must be true or false");
    }

    private static String decode(MultipartFile file) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(file.getBytes()))
                    .toString();
        } catch (CharacterCodingException exception) {
            throw badRequest("CSV_ENCODING_INVALID", "CSV files must use UTF-8 encoding.");
        } catch (IOException exception) {
            throw badRequest("CSV_READ_FAILED", "The CSV file could not be read.");
        }
    }

    public static ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }
}
