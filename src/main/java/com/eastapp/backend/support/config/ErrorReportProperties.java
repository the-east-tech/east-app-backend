package com.eastapp.backend.support.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "eastapp.error-report")
public class ErrorReportProperties {

    private boolean enabled;
    private String recipient = "";
    private String from = "";
    private long duplicateWindowSeconds = 300;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getRecipient() {
        return recipient;
    }

    public void setRecipient(String recipient) {
        this.recipient = valueOrEmpty(recipient);
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = valueOrEmpty(from);
    }

    public long getDuplicateWindowSeconds() {
        return duplicateWindowSeconds;
    }

    public void setDuplicateWindowSeconds(long duplicateWindowSeconds) {
        this.duplicateWindowSeconds = Math.max(duplicateWindowSeconds, 0);
    }

    public boolean isConfigured() {
        return enabled && !recipient.isBlank() && !from.isBlank();
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
