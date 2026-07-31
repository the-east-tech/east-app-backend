package com.eastapp.backend.reports.api;

import java.time.LocalDate;
import java.util.List;

public record ReportDashboardResponse(
        LocalDate asOfDate,
        int periodDays,
        boolean managementView,
        SalesOverviewResponse sales,
        PeriodInventoryHealthResponse periodInventory,
        WorkforceIntelligenceResponse workforce,
        InventoryIntelligenceResponse inventory,
        WasteOverviewResponse waste,
        DailyPhotoOverviewResponse dailyPhotos,
        ComplaintOverviewResponse complaints,
        int pendingApprovals,
        List<ReportTrendPointResponse> trend
) {
}
