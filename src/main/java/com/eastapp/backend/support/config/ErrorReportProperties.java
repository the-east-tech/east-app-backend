package com.eastapp.backend.support.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "eastapp.error-report")
public class ErrorReportProperties {
    private boolean enabled;
    private String apiKey = "";
    private String apiUrl = "https://api.resend.com/emails";
    private String recipient = "";
    private String from = "EastApp <onboarding@resend.dev>";
    private long duplicateWindowSeconds = 300;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = valueOrEmpty(apiKey); }
    public String getApiUrl() { return apiUrl; }
    public void setApiUrl(String apiUrl) { this.apiUrl = valueOrEmpty(apiUrl); }
    public String getRecipient() { return recipient; }
    public void setRecipient(String recipient) { this.recipient = valueOrEmpty(recipient); }
    public String getFrom() { return from; }
    public void setFrom(String from) { this.from = valueOrEmpty(from); }
    public long getDuplicateWindowSeconds() { return duplicateWindowSeconds; }
    public void setDuplicateWindowSeconds(long duplicateWindowSeconds) {
        this.duplicateWindowSeconds = Math.max(duplicateWindowSeconds, 0);
    }

    public boolean isConfigured() {
        return enabled
                && !apiKey.isBlank()
                && !apiUrl.isBlank()
                && !recipient.isBlank()
                && !from.isBlank();
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
