package com.eastapp.backend.appversion.api;

import com.eastapp.backend.appversion.AppPlatform;

public record AppVersionPolicyResponse(
        AppPlatform platform,
        String currentVersion,
        long currentBuildNumber,
        String latestVersion,
        long latestBuildNumber,
        boolean updateRequired,
        String updateUrl
) {
}
