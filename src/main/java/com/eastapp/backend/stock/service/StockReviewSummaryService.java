package com.eastapp.backend.stock.service;

import com.eastapp.backend.auth.security.AuthenticatedUser;
import com.eastapp.backend.stock.StockCountSubmissionRepository;
import com.eastapp.backend.stock.StockReceivingRepository;
import com.eastapp.backend.stock.StockSkuChangeRequestRepository;
import com.eastapp.backend.stock.StockWorkflowStatus;
import com.eastapp.backend.stock.api.StockReviewSummaryResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StockReviewSummaryService {
    private final StockCountSubmissionRepository countRepository;
    private final StockReceivingRepository receivingRepository;
    private final StockSkuChangeRequestRepository skuChangeRequestRepository;

    public StockReviewSummaryService(
            StockCountSubmissionRepository countRepository,
            StockReceivingRepository receivingRepository,
            StockSkuChangeRequestRepository skuChangeRequestRepository
    ) {
        this.countRepository = countRepository;
        this.receivingRepository = receivingRepository;
        this.skuChangeRequestRepository = skuChangeRequestRepository;
    }

    @Transactional(readOnly = true)
    public StockReviewSummaryResponse withOutstanding(
            AuthenticatedUser principal,
            StockReviewSummaryResponse today
    ) {
        String submitted = StockWorkflowStatus.SUBMITTED.name();
        long dailyCountPending = countRepository.countByTenant_IdAndReviewStatus(
                principal.tenantId(), submitted
        );
        long receivingPending = receivingRepository.countByTenant_IdAndReviewStatus(
                principal.tenantId(), submitted
        );
        long skuChangePending = skuChangeRequestRepository.countByTenantIdAndWorkflowStatus(
                principal.tenantId(), StockWorkflowStatus.SUBMITTED
        );
        return new StockReviewSummaryResponse(
                today.pendingReview(),
                today.done(),
                today.total(),
                dailyCountPending,
                receivingPending,
                skuChangePending
        );
    }
}
