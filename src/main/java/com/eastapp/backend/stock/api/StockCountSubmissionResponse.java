package com.eastapp.backend.stock.api;

import com.eastapp.backend.stock.StockCountSubmission;
import com.eastapp.backend.stock.StockWorkflowStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record StockCountSubmissionResponse(
        UUID id,
        UUID stockTaskId,
        String skuName,
        String skuUnit,
        String skuCategory,
        String skuLocation,
        String skuPhotoPath,
        BigDecimal skuMinimumBalanceValue,
        BigDecimal skuMaximumBalanceValue,
        String submittedBy,
        String submittedAt,
        Instant capturedAt,
        String stockPhotoName,
        String invoicePhotoName,
        BigDecimal previousBalanceValue,
        BigDecimal currentBalanceValue,
        boolean belowMinimumBalance,
        Map<String, Boolean> checkedItems,
        Map<String, String> remarks,
        StockWorkflowStatus workflowStatus,
        String reviewedBy,
        String reviewedAt,
        String reviewNote
) {
    public static StockCountSubmissionResponse from(
            StockCountSubmission item,
            String skuPhotoPath
    ) {
        return new StockCountSubmissionResponse(
                item.getId(), item.getSku().getId(),
                item.getSku().getName(), item.getSku().getUnit(),
                item.getSku().getCategory(), item.getSku().getLocation(), skuPhotoPath,
                item.getSku().getMinimumBalanceValue(), item.getSku().getMaximumBalanceValue(),
                StockResponseSupport.employeeId(item.getSubmittedBy()),
                StockResponseSupport.label(item.getCapturedAt()), item.getCapturedAt(),
                item.getStockPhotoName(), item.getInvoicePhotoName(),
                item.getPreviousBalanceValue(), item.getCurrentBalanceValue(),
                item.isBelowMinimumBalance(), Map.copyOf(item.getCheckedItems()),
                Map.copyOf(item.getRemarks()), item.getWorkflowStatus(),
                StockResponseSupport.employeeId(item.getReviewedBy()),
                StockResponseSupport.label(item.getReviewedAt()), item.getReviewNote()
        );
    }
}
