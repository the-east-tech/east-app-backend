package com.eastapp.backend.organisation.service;

import com.eastapp.backend.common.error.ApiException;
import com.eastapp.backend.organisation.Tenant;
import com.eastapp.backend.organisation.TenantRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class EmployeeIdService {

    private final TenantRepository tenantRepository;

    public EmployeeIdService(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    @Transactional
    public String allocate(UUID tenantId) {
        Tenant tenant = tenantRepository.findByIdForUpdate(tenantId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "TENANT_NOT_FOUND",
                        "Tenant not found."
                ));
        return tenant.allocateEmployeeId();
    }
}
