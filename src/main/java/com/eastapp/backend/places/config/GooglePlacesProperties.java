package com.eastapp.backend.places.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "eastapp.google-places")
public class GooglePlacesProperties {

    // Development setup: replace this single value with the Google Maps server key.
    private static final String HARDCODED_API_KEY = "AIzaSyDqshOy7FWWwqJsX5I1WA59-BSkfKohHdk";

    private String regionCode = "MY";
    private String languageCode = "en";
    private int ratingCacheMinutes = 60;

    public String getApiKey() {
        return HARDCODED_API_KEY;
    }

    public String getRegionCode() {
        return regionCode;
    }

    public void setRegionCode(String regionCode) {
        this.regionCode = requireText(regionCode, "regionCode").toUpperCase();
    }

    public String getLanguageCode() {
        return languageCode;
    }

    public void setLanguageCode(String languageCode) {
        this.languageCode = requireText(languageCode, "languageCode");
    }

    public int getRatingCacheMinutes() {
        return ratingCacheMinutes;
    }

    public void setRatingCacheMinutes(int ratingCacheMinutes) {
        if (ratingCacheMinutes < 1 || ratingCacheMinutes > 1440) {
            throw new IllegalArgumentException("ratingCacheMinutes must be between 1 and 1440");
        }
        this.ratingCacheMinutes = ratingCacheMinutes;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
