package com.eastapp.backend.stock.api;

import com.eastapp.backend.stock.StockReceiving;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record StockReceivingResponse(
        UUID id,
        UUID supplierId,
        String supplierName,
        String receivedBy,
        String receivedAt,
        Instant capturedAt,
        String invoicePhotoName,
        String goodsPhotoName,
        List<StockReceivingItemResponse> items,
        String reviewStatus,
        String reviewedBy,
        String reviewedAt,
        String reviewNote
) {
    public static StockReceivingResponse from(StockReceiving item) {
        return new StockReceivingResponse(
                item.getId(), item.getSupplier().getId(), item.getSupplier().getSupplierName(),
                item.getReceivedBy().getEmployeeId(), StockResponseSupport.label(item.getCapturedAt()),
                item.getCapturedAt(), item.getInvoicePhotoName(), item.getGoodsPhotoName(),
                item.getItems().stream().map(StockReceivingItemResponse::from).toList(),
                item.getReviewStatus(),
                item.getReviewedBy() == null ? "" : item.getReviewedBy().getEmployeeId(),
                StockResponseSupport.label(item.getReviewedAt()), item.getReviewNote()
        );
    }
}
