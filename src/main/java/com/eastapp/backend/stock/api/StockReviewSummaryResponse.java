package com.eastapp.backend.stock.api;

public record StockReviewSummaryResponse(
        long pendingReview,
        long done,
        long total,
        long dailyCountPending,
        long receivingPending,
        long skuChangePending
) {
    public StockReviewSummaryResponse(long pendingReview, long done, long total) {
        this(pendingReview, done, total, 0, 0, 0);
    }

    public long outstandingPending() {
        return dailyCountPending + receivingPending + skuChangePending;
    }
}
