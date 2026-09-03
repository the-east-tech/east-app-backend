package com.eastapp.backend.stock;

public enum StockWorkflowStatus {
    PENDING,
    SUBMITTED,
    DONE;

    public static StockWorkflowStatus fromStored(String value) {
        if (value == null || value.isBlank()) return SUBMITTED;
        return switch (value.trim()) {
            case "PENDING", "Rejected" -> PENDING;
            case "SUBMITTED", "Pending", "Pending Review" -> SUBMITTED;
            case "DONE", "Approved" -> DONE;
            default -> throw new IllegalArgumentException("Unknown stock workflow status: " + value);
        };
    }

    public static StockWorkflowStatus fromReviewAction(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("review status is required");
        }
        return switch (value.trim()) {
            case "PENDING", "Rejected" -> PENDING;
            case "DONE", "Approved" -> DONE;
            default -> throw new IllegalArgumentException("review status must be DONE or PENDING");
        };
    }

    public String legacyLabel() {
        return switch (this) {
            case PENDING -> "Rejected";
            case SUBMITTED -> "Pending Review";
            case DONE -> "Approved";
        };
    }

    public static String canonicalFilter(String value) {
        if (value == null || value.isBlank()) return null;
        return fromStored(value).name();
    }
}
