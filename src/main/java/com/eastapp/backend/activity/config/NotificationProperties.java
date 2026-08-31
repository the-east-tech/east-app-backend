package com.eastapp.backend.activity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "eastapp.notifications")
public class NotificationProperties {
    private boolean pushEnabled;
    private String firebaseProjectId = "";
    private String firebaseServiceAccountJson = "";

    public boolean isPushEnabled() { return pushEnabled; }
    public String getFirebaseProjectId() { return firebaseProjectId; }
    public String getFirebaseServiceAccountJson() { return firebaseServiceAccountJson; }

    public void setPushEnabled(boolean pushEnabled) {
        this.pushEnabled = pushEnabled;
    }

    public void setFirebaseProjectId(String firebaseProjectId) {
        this.firebaseProjectId = firebaseProjectId == null ? "" : firebaseProjectId.trim();
    }

    public void setFirebaseServiceAccountJson(String firebaseServiceAccountJson) {
        this.firebaseServiceAccountJson = firebaseServiceAccountJson == null
                ? ""
                : firebaseServiceAccountJson.trim();
    }

    public boolean canQueuePush() {
        return pushEnabled && !firebaseProjectId.isBlank();
    }
}
