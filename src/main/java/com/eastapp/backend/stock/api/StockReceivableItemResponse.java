package com.eastapp.backend.stock.api;

import com.eastapp.backend.stock.StockReceivableItem;
import java.math.BigDecimal;
import java.util.UUID;

public record StockReceivableItemResponse(
        UUID skuId,
        String skuName,
        BigDecimal invoiceQuantity,
        BigDecimal receivedQuantity,
        String unit,
        String condition,
        String note
) {
    public static StockReceivableItemResponse from(StockReceivableItem item) {
        return new StockReceivableItemResponse(
                item.getSku().getId(), item.getSkuName(), item.getInvoiceQuantity(),
                item.getReceivedQuantity(), item.getUnit(), item.getCondition(), item.getNote()
        );
    }
}
