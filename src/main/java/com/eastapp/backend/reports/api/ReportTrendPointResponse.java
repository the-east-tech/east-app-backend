package com.eastapp.backend.reports.api;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ReportTrendPointResponse(
        LocalDate date,
        BigDecimal netSalesRm,
        BigDecimal voidAmountRm,
        BigDecimal wasteLossRm
) {
}
