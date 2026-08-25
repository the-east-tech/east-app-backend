package com.eastapp.backend.stock.api;

import com.eastapp.backend.stock.StockTag;
import java.util.List;
import java.util.UUID;

public record StockTagResponse(
        UUID id,
        String tag,
        String createdBy,
        String createdDate,
        String lastUpdated,
        List<StockTagAssigneeResponse> assignedUsers
) {
    public static StockTagResponse from(StockTag item) {
        return from(item, List.of());
    }

    public static StockTagResponse from(
            StockTag item,
            List<StockTagAssigneeResponse> assignedUsers
    ) {
        return new StockTagResponse(
                item.getId(),
                item.getTag(),
                item.getCreatedBy().getEmployeeId(),
                StockResponseSupport.label(item.getCreatedAt()),
                StockResponseSupport.label(item.getUpdatedAt()),
                List.copyOf(assignedUsers)
        );
    }
}
