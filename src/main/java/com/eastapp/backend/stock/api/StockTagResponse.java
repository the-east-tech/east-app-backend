package com.eastapp.backend.stock.api;

import com.eastapp.backend.stock.StockTag;
import java.util.UUID;

public record StockTagResponse(
        UUID id,
        String tag,
        String createdBy,
        String createdDate,
        String lastUpdated
) {
    public static StockTagResponse from(StockTag item) {
        return new StockTagResponse(
                item.getId(),
                item.getTag(),
                item.getCreatedBy().getEmployeeId(),
                StockResponseSupport.label(item.getCreatedAt()),
                StockResponseSupport.label(item.getUpdatedAt())
        );
    }
}
