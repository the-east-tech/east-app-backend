package com.eastapp.backend.stock.api;

import com.eastapp.backend.stock.StockSupplier;
import java.math.BigDecimal;
import java.util.UUID;

public record StockSupplierResponse(
        UUID id,
        String supplierName,
        String supplierItem,
        String contactPerson,
        String phone,
        String address,
        String notes,
        String unit,
        BigDecimal recommendedPurchaseAmount,
        String recommendedPurchaseFrequency,
        BigDecimal pricingPerUnit,
        BigDecimal minimumBalanceValue,
        BigDecimal maximumBalanceValue,
        BigDecimal currentBalanceValue,
        String lastBalanceUpdatedAt,
        String lastBalanceUpdatedBy
) {
    public static StockSupplierResponse from(StockSupplier item) {
        return new StockSupplierResponse(
                item.getId(), item.getSupplierName(), item.getSupplierItem(),
                item.getContactPerson(), item.getPhone(), item.getAddress(), item.getNotes(),
                item.getUnit(), item.getRecommendedPurchaseAmount(),
                item.getRecommendedPurchaseFrequency(), item.getPricingPerUnit(),
                item.getMinimumBalanceValue(), item.getMaximumBalanceValue(),
                item.getCurrentBalanceValue(),
                StockResponseSupport.label(item.getLastBalanceUpdatedAt()),
                item.getLastBalanceUpdatedBy().getEmployeeId()
        );
    }
}
