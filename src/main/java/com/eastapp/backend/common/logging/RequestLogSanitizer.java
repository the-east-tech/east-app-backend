package com.eastapp.backend.common.logging;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Produces compact, single-line request data for development logs while masking
 * credentials and other values that would make the logs unsafe or unreadable.
 */
final class RequestLogSanitizer {

    private static final int MAX_QUERY_LENGTH = 2_000;
    private static final int MAX_BODY_LENGTH = 4_000;

    private static final Pattern JSON_SECRET = Pattern.compile(
            "(?i)(\\\"(?:password|newPassword|currentPassword|phoneE164|setupCode|token|accessToken|refreshToken|authorization|secret)\\\"\\s*:\\s*)\\\"(?:\\\\.|[^\\\"\\\\])*\\\""
    );
    private static final Pattern FORM_SECRET = Pattern.compile(
            "(?i)(^|&)((?:password|newPassword|currentPassword|phoneE164|setupCode|token|accessToken|refreshToken|authorization|secret)=)([^&]*)"
    );

    private RequestLogSanitizer() {
    }

    static String query(String queryString) {
        if (queryString == null || queryString.isBlank()) {
            return "-";
        }

        List<String> safeParts = new ArrayList<>();
        for (String part : queryString.split("&", -1)) {
            int separator = part.indexOf('=');
            String rawName = separator < 0 ? part : part.substring(0, separator);
            String rawValue = separator < 0 ? "" : part.substring(separator + 1);
            String decodedName = decode(rawName).toLowerCase(Locale.ROOT);
            if (isSensitiveName(decodedName)) {
                safeParts.add(rawName + "=<redacted>");
            } else {
                safeParts.add(separator < 0 ? rawName : rawName + "=" + rawValue);
            }
        }
        return truncate(String.join("&", safeParts), MAX_QUERY_LENGTH);
    }

    static String body(byte[] content, HttpServletRequest request) {
        if (content == null || content.length == 0) {
            return "-";
        }

        String contentType = request.getContentType();
        String normalisedType = contentType == null
                ? ""
                : contentType.toLowerCase(Locale.ROOT);

        if (normalisedType.startsWith("multipart/form-data")) {
            return "<multipart body sizeBytes=" + content.length + ">";
        }
        if (normalisedType.startsWith("image/")
                || normalisedType.contains("application/octet-stream")) {
            return "<binary body sizeBytes=" + content.length + ">";
        }

        Charset charset = request.getCharacterEncoding() == null
                ? StandardCharsets.UTF_8
                : Charset.forName(request.getCharacterEncoding());
        String raw = new String(content, charset);
        String redacted = redactSecrets(raw);
        return truncate(oneLine(redacted), MAX_BODY_LENGTH);
    }

    static String claimedCaller(byte[] content, HttpServletRequest request) {
        if (content == null || content.length == 0) {
            return "anonymous";
        }

        String contentType = request.getContentType();
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).contains("json")) {
            return "anonymous";
        }

        Charset charset = request.getCharacterEncoding() == null
                ? StandardCharsets.UTF_8
                : Charset.forName(request.getCharacterEncoding());
        String raw = new String(content, charset);
        String employeeId = jsonString(raw, "employeeId");
        String companyCode = jsonString(raw, "companyCode");
        String fullName = jsonString(raw, "fullName");

        if (employeeId != null && companyCode != null) {
            return "claimed:" + safeValue(companyCode) + "/" + safeValue(employeeId);
        }
        if (employeeId != null) {
            return "claimed:" + safeValue(employeeId);
        }
        if (fullName != null) {
            return "claimed:" + safeValue(fullName);
        }
        return "anonymous";
    }

    static String safeValue(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return truncate(oneLine(value.trim()), 160);
    }

    private static String redactSecrets(String value) {
        String jsonRedacted = JSON_SECRET.matcher(value).replaceAll("$1\"<redacted>\"");
        Matcher formMatcher = FORM_SECRET.matcher(jsonRedacted);
        StringBuffer result = new StringBuffer();
        while (formMatcher.find()) {
            formMatcher.appendReplacement(
                    result,
                    Matcher.quoteReplacement(formMatcher.group(1) + formMatcher.group(2) + "<redacted>")
            );
        }
        formMatcher.appendTail(result);
        return result.toString();
    }

    private static String jsonString(String json, String fieldName) {
        Pattern pattern = Pattern.compile(
                "(?i)\\\"" + Pattern.quote(fieldName)
                        + "\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"\\\\])*)\\\""
        );
        Matcher matcher = pattern.matcher(json);
        if (!matcher.find()) {
            return null;
        }
        return matcher.group(1)
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }

    private static boolean isSensitiveName(String name) {
        return name.contains("password")
                || name.contains("phone")
                || name.contains("token")
                || name.contains("authorization")
                || name.contains("secret")
                || name.contains("setupcode");
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            return value;
        }
    }

    private static String oneLine(String value) {
        return value.replaceAll("[\\r\\n\\t]+", " ")
                .replaceAll(" {2,}", " ")
                .trim();
    }

    private static String truncate(String value, int maximumLength) {
        if (value.length() <= maximumLength) {
            return value;
        }
        return value.substring(0, maximumLength - 18) + "...<truncated>";
    }
}
