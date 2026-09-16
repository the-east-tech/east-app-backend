package com.eastapp.backend.support.service;

import com.eastapp.backend.auth.security.AuthenticatedUser;
import com.eastapp.backend.common.error.ApiException;
import com.eastapp.backend.support.api.ErrorReportRequest;
import com.eastapp.backend.support.config.ErrorReportProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class ErrorReportService {
    private static final Logger log = LoggerFactory.getLogger(ErrorReportService.class);
    private static final int MAX_SYSTEM_REPORT_LENGTH = 40_000;

    private final ErrorReportProperties properties;
    private final JsonMapper jsonMapper;
    private final HttpClient httpClient;
    private final ConcurrentMap<String, Instant> lastSystemReports = new ConcurrentHashMap<>();

    public ErrorReportService(ErrorReportProperties properties, JsonMapper jsonMapper) {
        this.properties = properties;
        this.jsonMapper = jsonMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public void sendUserReport(AuthenticatedUser principal, ErrorReportRequest request) {
        requireConfigured();
        String body = """
                EastApp user error report

                Received: %s
                Reporter: %s (%s)
                Role: %s
                Business: %s (%s)

                Reported error
                %s

                Debug report
                %s
                """.formatted(
                Instant.now(),
                principal.fullName(),
                principal.employeeId(),
                principal.systemRole(),
                principal.tenantName(),
                principal.tenantCode(),
                request.errorDetails().trim(),
                request.debugReport().trim()
        );
        try {
            send("[EastApp User Error] " + request.reference().trim(), body);
        } catch (RuntimeException exception) {
            log.error("User error report email failed reference={}", request.reference(), exception);
            ApiException apiException = new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "ERROR_REPORT_EMAIL_FAILED",
                    "Automatic error report email failed: " + rootMessage(exception)
            );
            apiException.initCause(exception);
            throw apiException;
        }
    }

    public void reportSystemError(String source, Throwable error) {
        if (!properties.isConfigured()) {
            log.error("System error email is not configured source={}", source, error);
            return;
        }

        String fingerprint = source + '|' + error.getClass().getName() + '|' + rootMessage(error);
        Instant now = Instant.now();
        Instant duplicateCutoff = now.minusSeconds(properties.getDuplicateWindowSeconds());
        Instant previous = lastSystemReports.put(fingerprint, now);
        if (previous != null && previous.isAfter(duplicateCutoff)) return;

        Thread.startVirtualThread(() -> {
            try {
                send(
                        "[EastApp System Error] " + cleanSubject(source),
                        limit("""
                                EastApp automatic system error report

                                Occurred: %s
                                Source: %s

                                %s
                                """.formatted(now, source, stackTrace(error)), MAX_SYSTEM_REPORT_LENGTH)
                );
            } catch (RuntimeException exception) {
                log.error("System error report email failed source={}", source, exception);
            }
        });
    }

    private void send(String subject, String text) {
        try {
            String payload = jsonMapper.writeValueAsString(Map.of(
                    "from", properties.getFrom(),
                    "to", List.of(properties.getRecipient()),
                    "subject", limit(cleanSubject(subject), 180),
                    "text", text
            ));
            HttpRequest request = HttpRequest.newBuilder(URI.create(properties.getApiUrl()))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException(
                        "Resend returned HTTP " + response.statusCode() + ": "
                                + limit(response.body(), 1_000)
                );
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Resend request was interrupted", exception);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Resend payload could not be created", exception);
        } catch (IOException | IllegalArgumentException exception) {
            throw new IllegalStateException("Resend request failed: " + rootMessage(exception), exception);
        }
    }

    private void requireConfigured() {
        if (!properties.isConfigured()) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "ERROR_REPORT_EMAIL_NOT_CONFIGURED",
                    "Automatic error reporting is not configured on the server."
            );
        }
    }

    private static String cleanSubject(String value) {
        return value.replace('\r', ' ').replace('\n', ' ').trim();
    }

    private static String stackTrace(Throwable error) {
        var writer = new StringWriter();
        error.printStackTrace(new PrintWriter(writer));
        return writer.toString().trim();
    }

    private static String rootMessage(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        String message = root.getMessage();
        return limit(root.getClass().getSimpleName()
                + (message == null || message.isBlank() ? "" : ": " + message.trim()), 1_000);
    }

    private static String limit(String value, int maximumLength) {
        if (value == null) return "";
        return value.length() <= maximumLength
                ? value
                : value.substring(0, maximumLength - 3) + "...";
    }
}
