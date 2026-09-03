package com.eastapp.backend.stock.api;

import com.eastapp.backend.stock.StockCountSubmission;
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
        String reviewStatus,
        String workflowStatus,
        String reviewedBy,
        String reviewedAt,
        String reviewNote
) {
    public static StockCountSubmissionResponse from(StockCountSubmission item) {
        return new StockCountSubmissionResponse(
                item.getId(), item.getSku().getId(),
                item.getSku().getName(), item.getSku().getUnit(),
                item.getSku().getCategory(), item.getSku().getLocation(), item.getSku().getPhotoPath(),
                item.getSku().getMinimumBalanceValue(), item.getSku().getMaximumBalanceValue(),
                item.getSubmittedBy().getEmployeeId(),
                StockResponseSupport.label(item.getCapturedAt()), item.getCapturedAt(),
                item.getStockPhotoName(), item.getInvoicePhotoName(),
                item.getPreviousBalanceValue(), item.getCurrentBalanceValue(),
                item.isBelowMinimumBalance(), Map.copyOf(item.getCheckedItems()),
                Map.copyOf(item.getRemarks()), item.getReviewStatus(), item.getWorkflowStatus(),
                item.getReviewedBy() == null ? "" : item.getReviewedBy().getEmployeeId(),
                StockResponseSupport.label(item.getReviewedAt()), item.getReviewNote()
        );
    }
}
