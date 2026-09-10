package com.eastapp.backend.storage.api;

import jakarta.validation.constraints.Min;

public record StorageCleanupRequest(
        boolean confirmed,
        @Min(1) int rowCount
) {
}
