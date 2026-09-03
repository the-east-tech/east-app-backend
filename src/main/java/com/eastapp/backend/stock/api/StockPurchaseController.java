package com.eastapp.backend.stock.api;

import com.eastapp.backend.auth.security.AuthenticatedUser;
import com.eastapp.backend.stock.service.StockPurchaseService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/stock/purchases")
@PreAuthorize("hasAnyRole('OWNER', 'HEAD', 'MANAGER')")
public class StockPurchaseController {
    private final StockPurchaseService service;

    public StockPurchaseController(StockPurchaseService service) {
        this.service = service;
    }

    @GetMapping("/suppliers")
    List<StockPurchaseSupplierResponse> suppliers(
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        return service.suppliers(principal);
    }

    @PatchMapping("/suppliers/{supplierId}/template")
    StockPurchaseSupplierResponse updateTemplate(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID supplierId,
            @Valid @RequestBody UpdatePurchaseMessageTemplateRequest request
    ) {
        return service.updateTemplate(principal, supplierId, request);
    }

    @PostMapping("/suppliers/{supplierId}/ordered")
    StockPurchaseSupplierResponse markOrdered(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID supplierId,
            @Valid @RequestBody MarkSupplierOrderedRequest request
    ) {
        return service.markOrdered(principal, supplierId, request);
    }
}
