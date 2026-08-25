package com.eastapp.backend.tasks.api;

import java.time.LocalDate;
import java.util.List;

public record DailyTaskListResponse(
        LocalDate taskDate,
        LocalDate dateFrom,
        LocalDate dateTo,
        DailyTaskOverviewResponse overview,
        List<DailyTaskRecordResponse> records
) {
}
