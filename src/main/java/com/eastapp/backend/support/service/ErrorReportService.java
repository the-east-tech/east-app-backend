package com.eastapp.backend.support.service;

import com.eastapp.backend.auth.security.AuthenticatedUser;
import com.eastapp.backend.common.error.ApiException;
import com.eastapp.backend.support.api.ErrorReportRequest;
import com.eastapp.backend.support.config.ErrorReportProperties;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class ErrorReportService {

    private static final Logger log = LoggerFactory.getLogger(ErrorReportService.class);
    private static final int MAX_SYSTEM_REPORT_LENGTH = 40_000;

    private final JavaMailSender mailSender;
    private final ErrorReportProperties properties;
    private final ConcurrentMap<String, Instant> lastSystemReports = new ConcurrentHashMap<>();

    public ErrorReportService(JavaMailSender mailSender, ErrorReportProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    public void sendUserReport(AuthenticatedUser principal, ErrorReportRequest request) {
        requireConfigured();
        var message = baseMessage("[EastApp User Error] " + request.reference().trim());
        message.setText("""
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
        ));
        try {
            mailSender.send(message);
        } catch (MailException exception) {
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
                var message = baseMessage("[EastApp System Error] " + cleanSubject(source));
                message.setText(limit("""
                        EastApp automatic system error report

                        Occurred: %s
                        Source: %s

                        %s
                        """.formatted(now, source, stackTrace(error)), MAX_SYSTEM_REPORT_LENGTH));
                mailSender.send(message);
            } catch (RuntimeException mailException) {
                log.error("System error report email failed source={}", source, mailException);
            }
        });
    }

    private SimpleMailMessage baseMessage(String subject) {
        var message = new SimpleMailMessage();
        message.setFrom(properties.getFrom());
        message.setTo(properties.getRecipient());
        message.setSubject(limit(cleanSubject(subject), 180));
        return message;
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
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage();
        String value = root.getClass().getSimpleName()
                + (message == null || message.isBlank() ? "" : ": " + message.trim());
        return limit(value, 1_000);
    }

    private static String limit(String value, int maximumLength) {
        return value.length() <= maximumLength
                ? value
                : value.substring(0, maximumLength - 3) + "...";
    }
}
