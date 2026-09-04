package com.eastapp.backend.reports;

import java.util.UUID;

public record ReportMediaReference(UUID id, String storageKey, UUID uploadedByUserId) {
}
