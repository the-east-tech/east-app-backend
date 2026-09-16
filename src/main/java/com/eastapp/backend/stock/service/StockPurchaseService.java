package com.eastapp.backend.stock.service;

import com.eastapp.backend.auth.security.AuthenticatedUser;
import com.eastapp.backend.common.error.ApiException;
import com.eastapp.backend.stock.StockSupplier;
import com.eastapp.backend.stock.StockSupplierRepository;
import com.eastapp.backend.stock.api.StockPurchaseSupplierResponse;
import com.eastapp.backend.stock.api.UpdatePurchaseMessageTemplateRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class StockPurchaseService {
    private final StockSupplierRepository supplierRepository;

    public StockPurchaseService(StockSupplierRepository supplierRepository) {
        this.supplierRepository = supplierRepository;
    }

    @Transactional(readOnly = true)
    public List<StockPurchaseSupplierResponse> suppliers(AuthenticatedUser principal) {
        return supplierRepository.findAllByTenant_IdAndActiveTrueOrderBySupplierNameAsc(
                        principal.tenantId()
                )
                .stream()
                .map(StockPurchaseSupplierResponse::from)
                .toList();
    }

    @Transactional
    public StockPurchaseSupplierResponse updateTemplate(
            AuthenticatedUser principal,
            UUID supplierId,
            UpdatePurchaseMessageTemplateRequest request
    ) {
        StockSupplier supplier = supplierForUpdate(principal, supplierId);
        supplier.updatePurchaseMessageTemplate(request.messageTemplate());
        return StockPurchaseSupplierResponse.from(supplier);
    }

    private StockSupplier supplierForUpdate(AuthenticatedUser principal, UUID supplierId) {
        return supplierRepository.findLockedByIdAndTenant_Id(supplierId, principal.tenantId())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "STOCK_SUPPLIER_NOT_FOUND",
                        "Supplier not found."
                ));
    }

}
