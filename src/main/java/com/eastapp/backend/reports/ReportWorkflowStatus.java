package com.eastapp.backend.reports;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ReportWorkflowStatus {
    PENDING,
    SUBMITTED,
    DONE;

    public static final ReportWorkflowStatus DRAFT = PENDING;
    public static final ReportWorkflowStatus APPROVED = DONE;
    public static final ReportWorkflowStatus REJECTED = PENDING;

    @JsonCreator
    public static ReportWorkflowStatus fromJson(String value) {
        if (value == null || value.isBlank()) return null;
        return switch (value.trim().toUpperCase()) {
            case "PENDING", "DRAFT", "REJECTED" -> PENDING;
            case "SUBMITTED" -> SUBMITTED;
            case "DONE", "APPROVED" -> DONE;
            default -> throw new IllegalArgumentException("Unknown report workflow status: " + value);
        };
    }

    @JsonValue
    public String toJson() {
        return switch (this) {
            case PENDING -> "DRAFT";
            case SUBMITTED -> "SUBMITTED";
            case DONE -> "APPROVED";
        };
    }
}
