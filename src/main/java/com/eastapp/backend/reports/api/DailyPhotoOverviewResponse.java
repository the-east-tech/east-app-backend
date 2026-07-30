package com.eastapp.backend.reports.api;

public record DailyPhotoOverviewResponse(
        int currentUserPhotoCount,
        int minimumRequired,
        boolean currentUserComplete,
        int requiredStaffCount,
        int completedStaffCount,
        double completionRatePercent
) {
}
