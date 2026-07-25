package com.eastapp.backend.stock.api;

import java.util.List;

public record StockSnapshotResponse(
        List<StockTagResponse> tags,
        List<StockSupplierResponse> suppliers,
        List<StockSkuResponse> skus,
        List<StockCountSubmissionResponse> submissions,
        List<StockReceivingResponse> receivingRecords
) {
}
