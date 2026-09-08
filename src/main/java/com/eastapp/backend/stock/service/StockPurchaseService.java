package com.eastapp.backend.stock.service;

import com.eastapp.backend.auth.security.AuthenticatedUser;
import com.eastapp.backend.common.error.ApiException;
import com.eastapp.backend.people.UserAccount;
import com.eastapp.backend.people.UserAccountRepository;
import com.eastapp.backend.stock.StockSupplier;
import com.eastapp.backend.stock.StockSupplierRepository;
import com.eastapp.backend.stock.api.MarkSupplierOrderedRequest;
import com.eastapp.backend.stock.api.StockPurchaseSupplierResponse;
import com.eastapp.backend.stock.api.UpdatePurchaseMessageTemplateRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class StockPurchaseService {
    private final StockSupplierRepository supplierRepository;
    private final UserAccountRepository userRepository;

    public StockPurchaseService(
            StockSupplierRepository supplierRepository,
            UserAccountRepository userRepository
    ) {
        this.supplierRepository = supplierRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<StockPurchaseSupplierResponse> suppliers(AuthenticatedUser principal) {
        return supplierRepository.findAllByTenant_IdOrderBySupplierNameAsc(principal.tenantId())
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

    @Transactional
    public StockPurchaseSupplierResponse markOrdered(
            AuthenticatedUser principal,
            UUID supplierId,
            MarkSupplierOrderedRequest request
    ) {
        StockSupplier supplier = supplierForUpdate(principal, supplierId);
        UserAccount actor = actor(principal);
        try {
            supplier.markOrdered(request.message(), actor, Instant.now());
        } catch (IllegalStateException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "STOCK_ORDER_ALREADY_ACTIVE", exception.getMessage());
        }
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

    private UserAccount actor(AuthenticatedUser principal) {
        return userRepository.findByIdAndTenant_Id(principal.userId(), principal.tenantId())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "USER_NOT_FOUND",
                        "User not found."
                ));
    }
}
