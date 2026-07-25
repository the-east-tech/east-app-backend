package com.eastapp.backend.stock.api;

import com.eastapp.backend.stock.StockAuditEntry;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record StockAuditEntryResponse(
        UUID id,
        String module,
        String action,
        String itemId,
        String itemName,
        String actorName,
        String actorId,
        String actorRole,
        String timestampText,
        Instant capturedAt,
        List<StockAuditChangeResponse> changes,
        String note
) {
    public static StockAuditEntryResponse from(StockAuditEntry item) {
        return new StockAuditEntryResponse(
                item.getId(), item.getModule(), item.getAction(),
                item.getItemId() == null ? "" : item.getItemId().toString(), item.getItemName(),
                item.getActorName(), item.getActorEmployeeId(), item.getActorRole(),
                StockResponseSupport.label(item.getCapturedAt()), item.getCapturedAt(),
                item.getChanges().stream().map(StockAuditChangeResponse::from).toList(), item.getNote()
        );
    }
}
