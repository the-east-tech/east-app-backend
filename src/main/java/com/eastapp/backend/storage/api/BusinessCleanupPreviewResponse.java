package com.eastapp.backend.storage.api;

import java.util.List;

public record BusinessCleanupPreviewResponse(
        long recordCount,
        long blockedRecordCount,
        long photoCount,
        long excludedPhotoCount,
        long estimatedZipBytes,
        List<Category> categories
) {
    public record Category(
            BusinessCleanupDataType dataType,
            long recordCount,
            long blockedRecordCount,
            String description
    ) {
    }
}
