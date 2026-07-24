package com.eastapp.backend.attendance.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.ZoneId;

@Component
@ConfigurationProperties(prefix = "eastapp.attendance")
public class AttendanceProperties {

    private String timeZone = "Asia/Kuala_Lumpur";
    private String locationName = "Secret Coffee House";
    private double latitude = 4.3272472;
    private double longitude = 101.1329829;
    private int allowedRadiusMeters = 100;

    public ZoneId zoneId() {
        return ZoneId.of(timeZone);
    }

    public String getTimeZone() {
        return timeZone;
    }

    public void setTimeZone(String timeZone) {
        this.timeZone = requireText(timeZone, "timeZone");
        ZoneId.of(this.timeZone);
    }

    public String getLocationName() {
        return locationName;
    }

    public void setLocationName(String locationName) {
        this.locationName = requireText(locationName, "locationName");
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        if (latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException("attendance latitude must be between -90 and 90");
        }
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        if (longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("attendance longitude must be between -180 and 180");
        }
        this.longitude = longitude;
    }

    public int getAllowedRadiusMeters() {
        return allowedRadiusMeters;
    }

    public void setAllowedRadiusMeters(int allowedRadiusMeters) {
        if (allowedRadiusMeters <= 0) {
            throw new IllegalArgumentException("attendance allowed radius must be greater than zero");
        }
        this.allowedRadiusMeters = allowedRadiusMeters;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
