package com.eastapp.backend.translation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "eastapp.translation")
public class TranslationProperties {

    private boolean providerEnabled;
    private String cloudflareAccountId = "";
    private String cloudflareApiToken = "";
    private String model = "@cf/meta/m2m100-1.2b";
    private int maximumParallelRequests = 6;

    public boolean isProviderEnabled() {
        return providerEnabled;
    }

    public void setProviderEnabled(boolean providerEnabled) {
        this.providerEnabled = providerEnabled;
    }

    public String getCloudflareAccountId() {
        return cloudflareAccountId;
    }

    public void setCloudflareAccountId(String cloudflareAccountId) {
        this.cloudflareAccountId = valueOrEmpty(cloudflareAccountId);
    }

    public String getCloudflareApiToken() {
        return cloudflareApiToken;
    }

    public void setCloudflareApiToken(String cloudflareApiToken) {
        this.cloudflareApiToken = valueOrEmpty(cloudflareApiToken);
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        String value = valueOrEmpty(model);
        if (!value.isEmpty()) {
            this.model = value;
        }
    }

    public int getMaximumParallelRequests() {
        return maximumParallelRequests;
    }

    public void setMaximumParallelRequests(int maximumParallelRequests) {
        if (maximumParallelRequests < 1 || maximumParallelRequests > 12) {
            throw new IllegalArgumentException("maximumParallelRequests must be between 1 and 12");
        }
        this.maximumParallelRequests = maximumParallelRequests;
    }

    public boolean isConfigured() {
        return providerEnabled && !cloudflareAccountId.isBlank() && !cloudflareApiToken.isBlank();
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
