package com.eastapp.backend.appversion.config;

import com.eastapp.backend.appversion.AppPlatform;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "eastapp.app-version")
public class AppVersionProperties {
    private Release android = new Release();
    private Release ios = new Release();

    public Release getAndroid() {
        return android;
    }

    public void setAndroid(Release android) {
        this.android = android == null ? new Release() : android;
    }

    public Release getIos() {
        return ios;
    }

    public void setIos(Release ios) {
        this.ios = ios == null ? new Release() : ios;
    }

    public Release forPlatform(AppPlatform platform) {
        return platform == AppPlatform.IOS ? ios : android;
    }

    public static class Release {
        private String latestVersion = "1.0.0";
        private long latestBuildNumber = 6;
        private String updateUrl = "";

        public String getLatestVersion() {
            return latestVersion;
        }

        public void setLatestVersion(String latestVersion) {
            this.latestVersion = valueOrEmpty(latestVersion);
        }

        public long getLatestBuildNumber() {
            return latestBuildNumber;
        }

        public void setLatestBuildNumber(long latestBuildNumber) {
            this.latestBuildNumber = Math.max(latestBuildNumber, 1);
        }

        public String getUpdateUrl() {
            return updateUrl;
        }

        public void setUpdateUrl(String updateUrl) {
            this.updateUrl = valueOrEmpty(updateUrl);
        }

        private static String valueOrEmpty(String value) {
            return value == null ? "" : value.trim();
        }
    }
}
