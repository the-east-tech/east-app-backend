package com.eastapp.backend.reports.api;

public record ReportMediaUploadResponse(
        String storageKey,
        String contentType,
        long sizeBytes
) {
}
