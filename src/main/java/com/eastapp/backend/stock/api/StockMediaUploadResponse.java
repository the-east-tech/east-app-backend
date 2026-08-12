package com.eastapp.backend.stock.api;

public record StockMediaUploadResponse(
        String storageKey,
        String contentType,
        long sizeBytes
) {}
