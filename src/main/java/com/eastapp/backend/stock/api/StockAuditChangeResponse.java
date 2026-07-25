package com.eastapp.backend.stock.api;

import com.eastapp.backend.stock.StockAuditChange;

public record StockAuditChangeResponse(String field, String oldValue, String newValue) {
    public static StockAuditChangeResponse from(StockAuditChange item) {
        return new StockAuditChangeResponse(item.getField(), item.getOldValue(), item.getNewValue());
    }
}
