package com.eastapp.backend.stock.api;

import java.util.List;

public record CopyStockSkusResponse(
        int skusCopied,
        int tagsCopied,
        int suppliersCopied,
        List<StockSkuResponse> skus
) {
}
