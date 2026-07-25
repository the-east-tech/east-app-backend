package com.eastapp.backend.stock.api;

import com.eastapp.backend.stock.StockReceivingItem;
import java.math.BigDecimal;
import java.util.UUID;

public record StockReceivingItemResponse(
        UUID skuId,
        String skuName,
        BigDecimal invoiceQuantity,
        BigDecimal receivedQuantity,
        String unit,
        String condition,
        String note
) {
    public static StockReceivingItemResponse from(StockReceivingItem item) {
        return new StockReceivingItemResponse(
                item.getSku().getId(), item.getSkuName(), item.getInvoiceQuantity(),
                item.getReceivedQuantity(), item.getUnit(), item.getCondition(), item.getNote()
        );
    }
}
