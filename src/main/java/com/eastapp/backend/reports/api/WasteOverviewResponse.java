package com.eastapp.backend.reports.api;

import java.math.BigDecimal;

public record WasteOverviewResponse(
        BigDecimal todayLossRm,
        BigDecimal periodLossRm,
        BigDecimal wasteToNetSalesPercent,
        String topWasteItem,
        BigDecimal topWasteItemLossRm
) {
}
