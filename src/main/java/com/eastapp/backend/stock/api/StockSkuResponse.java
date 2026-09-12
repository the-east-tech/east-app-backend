package com.eastapp.backend.stock.api;

import com.eastapp.backend.stock.StockCheckSchedule;
import com.eastapp.backend.stock.StockSku;
import java.math.BigDecimal;
import java.time.LocalDate;
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
        List<String> receivableChecklist,
        StockCheckSchedule stockCheckSchedule,
        Integer stockCheckDay,
        LocalDate stockCheckDate,
        String lastUpdatedAt,
        String lastUpdatedBy,
        boolean active,
        boolean coolingPeriod
) {
    public static StockSkuResponse from(StockSku item, String photoPath) {
        return new StockSkuResponse(
                item.getId(), item.getName(),
                item.getTag1() == null ? null : item.getTag1().getId(), item.getCategory(),
                item.getTag2() == null ? null : item.getTag2().getId(), item.getLocation(),
                item.getUnit(), item.getMinimumBalanceValue(),
                item.getMaximumBalanceValue(), item.getCurrentBalanceValue(),
                item.getRecoveryPercent(), item.getMinimumPriceRm(),
                item.getMaximumPriceRm(),
                item.getSuppliers().stream().map(supplier -> supplier.getId()).toList(),
                photoPath, List.copyOf(item.getAssignedStaffNames()),
                List.copyOf(item.getReceivableChecklist()),
                item.getStockCheckSchedule(), item.getStockCheckDay(), item.getStockCheckDate(),
                StockResponseSupport.label(item.getUpdatedAt()),
                StockResponseSupport.employeeId(item.getLastUpdatedBy()), item.isActive(), item.isCoolingPeriod()
        );
    }
}
