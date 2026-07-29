package com.eastapp.backend.stock.api;

public record StockReviewSummaryResponse(
        long pendingReview,
        long done,
        long total
) {}
