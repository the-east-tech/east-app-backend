package com.eastapp.backend.reports.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.ZoneId;

@Component
@ConfigurationProperties(prefix = "eastapp.reports")
public class ReportProperties {
    private String timeZone = "Asia/Kuala_Lumpur";
    private int dailyPhotoMinimum = 5;

    public ZoneId zoneId() {
        return ZoneId.of(timeZone);
    }

    public String getTimeZone() { return timeZone; }

    public void setTimeZone(String timeZone) {
        if (timeZone == null || timeZone.trim().isEmpty()) {
            throw new IllegalArgumentException("timeZone must not be blank");
        }
        this.timeZone = timeZone.trim();
        ZoneId.of(this.timeZone);
    }

    public int getDailyPhotoMinimum() { return dailyPhotoMinimum; }

    public void setDailyPhotoMinimum(int dailyPhotoMinimum) {
        if (dailyPhotoMinimum < 1 || dailyPhotoMinimum > 20) {
            throw new IllegalArgumentException("dailyPhotoMinimum must be between 1 and 20");
        }
        this.dailyPhotoMinimum = dailyPhotoMinimum;
    }
}
