package com.eastapp.backend.tasks.api;

import java.time.LocalDate;
import java.util.List;

public record TaskListResponse(
        LocalDate taskDate,
        LocalDate dateFrom,
        LocalDate dateTo,
        TaskOverviewResponse overview,
        List<TaskRecordResponse> records
) {
}
