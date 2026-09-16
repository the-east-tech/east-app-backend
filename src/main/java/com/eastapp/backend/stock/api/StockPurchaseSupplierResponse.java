package com.eastapp.backend.stock.api;

import com.eastapp.backend.stock.StockSupplier;

import java.util.UUID;

public record StockPurchaseSupplierResponse(
        UUID supplierId,
        String messageTemplate
) {
    public static StockPurchaseSupplierResponse from(StockSupplier supplier) {
        return new StockPurchaseSupplierResponse(
                supplier.getId(),
                supplier.getPurchaseMessageTemplate()
        );
    }
}
