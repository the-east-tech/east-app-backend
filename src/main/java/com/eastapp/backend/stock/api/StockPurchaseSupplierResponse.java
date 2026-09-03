package com.eastapp.backend.stock.api;

import com.eastapp.backend.stock.StockSupplier;

import java.time.Instant;
import java.util.UUID;

public record StockPurchaseSupplierResponse(
        UUID supplierId,
        String messageTemplate,
        String orderState,
        boolean receivingEnabled,
        UUID currentOrderReference,
        Instant orderedAt,
        String orderedBy,
        String orderedMessage
) {
    public static StockPurchaseSupplierResponse from(StockSupplier supplier) {
        return new StockPurchaseSupplierResponse(
                supplier.getId(),
                supplier.getPurchaseMessageTemplate(),
                supplier.getOrderState(),
                supplier.canReceive(),
                supplier.getCurrentOrderReference(),
                supplier.getOrderedAt(),
                supplier.getOrderedBy() == null ? "" : supplier.getOrderedBy().getFullName(),
                supplier.getOrderedMessage()
        );
    }
}
