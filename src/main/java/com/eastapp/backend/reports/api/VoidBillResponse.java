package com.eastapp.backend.reports.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record VoidBillResponse(
        UUID id,
        String billNumber,
        String reason,
        BigDecimal amountRm,
        String photoStorageKey,
        String createdByName,
        Instant createdAt
) {
}
