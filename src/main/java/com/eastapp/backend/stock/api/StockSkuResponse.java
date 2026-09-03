package com.eastapp.backend.stock.api;

import com.eastapp.backend.stock.StockSku;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record StockSkuResponse(
        UUID id,
        String name,
        UUID tag1Id,
        String category,
        UUID tag2Id,
        String location,
        String unit,
        BigDecimal minimumBalanceValue,
        BigDecimal maximumBalanceValue,
        BigDecimal currentBalanceValue,
        int recoveryPercent,
        BigDecimal minimumPriceRm,
        BigDecimal maximumPriceRm,
        List<UUID> supplierIds,
        String photoPath,
        List<String> assignedStaffNames,
        List<String> receivingChecklist,
        int stockCheckFrequencyDays,
        String resetTime,
        String lastUpdatedAt,
        String lastUpdatedBy,
        boolean active,
        boolean coolingPeriod
) {
    public static StockSkuResponse from(StockSku item) {
        return new StockSkuResponse(
                item.getId(), item.getName(),
                item.getTag1() == null ? null : item.getTag1().getId(), item.getCategory(),
                item.getTag2() == null ? null : item.getTag2().getId(), item.getLocation(),
                item.getUnit(), item.getMinimumBalanceValue(),
                item.getMaximumBalanceValue(), item.getCurrentBalanceValue(),
                item.getRecoveryPercent(), item.getMinimumPriceRm(),
                item.getMaximumPriceRm(),
                item.getSuppliers().stream().map(supplier -> supplier.getId()).toList(),
                item.getPhotoPath(), List.copyOf(item.getAssignedStaffNames()),
                List.copyOf(item.getReceivingChecklist()),
                item.getStockCheckFrequencyDays(), item.getResetTime().toString(),
                StockResponseSupport.label(item.getUpdatedAt()),
                item.getLastUpdatedBy().getEmployeeId(), item.isActive(), item.isCoolingPeriod()
        );
    }
}
