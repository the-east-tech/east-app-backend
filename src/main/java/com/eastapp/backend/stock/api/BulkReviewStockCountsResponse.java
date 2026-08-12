package com.eastapp.backend.stock.api;

import java.util.List;

public record BulkReviewStockCountsResponse(
        int reviewedCount,
        List<StockCountSubmissionResponse> records
) {
}
