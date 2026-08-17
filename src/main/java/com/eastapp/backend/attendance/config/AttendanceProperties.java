package com.eastapp.backend.attendance.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.ZoneId;

@Component
@ConfigurationProperties(prefix = "eastapp.attendance")
public class AttendanceProperties {

    private String timeZone = "Asia/Kuala_Lumpur";

    public ZoneId zoneId() {
        return ZoneId.of(timeZone);
    }

    public String getTimeZone() {
        return timeZone;
    }

    public void setTimeZone(String timeZone) {
        if (timeZone == null || timeZone.trim().isEmpty()) {
            throw new IllegalArgumentException("timeZone must not be blank");
        }
        this.timeZone = timeZone.trim();
        ZoneId.of(this.timeZone);
    }
}
