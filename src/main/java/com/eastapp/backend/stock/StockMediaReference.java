package com.eastapp.backend.stock;

import java.util.UUID;

public record StockMediaReference(UUID id, String storageKey) {
    public String photoPath() {
        return storageKey.startsWith(StockMedia.SKU_IMPORT_PLACEHOLDER_PREFIX)
                ? ""
                : storageKey;
    }
}
