package com.eastapp.backend.tasks.api;

public record TaskOverviewResponse(
        int total,
        int pending,
        int submitted,
        int done
) {
    public double completionRatePercent() {
        return total == 0 ? 0.0 : done * 100.0 / total;
    }
}
