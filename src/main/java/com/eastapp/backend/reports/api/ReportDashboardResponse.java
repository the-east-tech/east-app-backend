package com.eastapp.backend.reports.api;

import com.eastapp.backend.tasks.api.TaskOverviewResponse;

import java.time.LocalDate;
import java.util.List;

public record ReportDashboardResponse(
        LocalDate asOfDate,
        int periodDays,
        boolean managementView,
        SalesOverviewResponse sales,
        PeriodCountCoverageResponse countCoverage,
        WorkforceIntelligenceResponse workforce,
        InventoryIntelligenceResponse inventory,
        WasteOverviewResponse waste,
        DailyPhotoOverviewResponse dailyPhotos,
        TaskOverviewResponse tasks,
        ComplaintOverviewResponse complaints,
        int pendingApprovals,
        List<ReportTrendPointResponse> trend
) {
}
