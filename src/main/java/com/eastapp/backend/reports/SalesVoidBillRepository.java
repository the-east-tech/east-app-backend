package com.eastapp.backend.reports;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SalesVoidBillRepository extends JpaRepository<SalesVoidBill, UUID> {
    List<SalesVoidBill> findAllByTenantIdAndSalesReportIdOrderByCreatedAtAsc(
            UUID tenantId,
            UUID salesReportId
    );

    List<SalesVoidBill> findAllByTenantIdAndSalesReportIdIn(
            UUID tenantId,
            List<UUID> salesReportIds
    );

    boolean existsByTenantIdAndSalesReportIdAndBillNumberIgnoreCase(
            UUID tenantId,
            UUID salesReportId,
            String billNumber
    );
}
