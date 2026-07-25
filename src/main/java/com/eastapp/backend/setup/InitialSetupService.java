package com.eastapp.backend.setup;

import com.eastapp.backend.auth.LoginIdentity;
import com.eastapp.backend.auth.LoginIdentityRepository;
import com.eastapp.backend.common.error.ApiException;
import com.eastapp.backend.organisation.Tenant;
import com.eastapp.backend.organisation.TenantRepository;
import com.eastapp.backend.organisation.service.TenantProvisioningService;
import com.eastapp.backend.setup.api.CompleteInitialSetupRequest;
import com.eastapp.backend.setup.api.CompleteInitialSetupResponse;
import com.eastapp.backend.setup.api.SetupStatusResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InitialSetupService {

    private static final Logger log = LoggerFactory.getLogger(InitialSetupService.class);
    private static final long SETUP_ADVISORY_LOCK = 4_503_421_177L;

    private final LoginIdentityRepository loginIdentityRepository;
    private final TenantRepository tenantRepository;
    private final PasswordEncoder passwordEncoder;
    private final SetupCodeService setupCodeService;
    private final TenantProvisioningService tenantProvisioningService;
    private final JdbcTemplate jdbcTemplate;

    public InitialSetupService(
            LoginIdentityRepository loginIdentityRepository,
            TenantRepository tenantRepository,
            PasswordEncoder passwordEncoder,
            SetupCodeService setupCodeService,
            TenantProvisioningService tenantProvisioningService,
            JdbcTemplate jdbcTemplate
    ) {
        this.loginIdentityRepository = loginIdentityRepository;
        this.tenantRepository = tenantRepository;
        this.passwordEncoder = passwordEncoder;
        this.setupCodeService = setupCodeService;
        this.tenantProvisioningService = tenantProvisioningService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void announceSetupIfRequired() {
        if (isSetupRequired()) {
            setupCodeService.ensureActiveCode();
        } else {
            log.info("EastApp initial setup is already complete");
        }
    }

    @Transactional
    public SetupStatusResponse status() {
        boolean required = isSetupRequired();
        if (required) {
            setupCodeService.ensureActiveCode();
        }
        return new SetupStatusResponse(required);
    }

    @Transactional
    public CompleteInitialSetupResponse complete(CompleteInitialSetupRequest request) {
        jdbcTemplate.execute("select pg_advisory_xact_lock(" + SETUP_ADVISORY_LOCK + ")");

        if (!isSetupRequired()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "SETUP_ALREADY_COMPLETED",
                    "Initial setup has already been completed."
            );
        }
        if (!setupCodeService.matches(request.setupCode())) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    "INVALID_SETUP_CODE",
                    "The setup code is invalid or has expired."
            );
        }

        String companyCode = Tenant.normaliseCode(request.companyCode());
        String prefix = Tenant.normaliseEmployeeIdPrefix(request.employeeIdPrefix());
        if (tenantRepository.existsByCompanyCode(companyCode)) {
            throw new ApiException(HttpStatus.CONFLICT, "COMPANY_CODE_EXISTS", "This company code already exists.");
        }
        if (tenantRepository.existsByEmployeeIdPrefix(prefix)) {
            throw new ApiException(HttpStatus.CONFLICT, "EMPLOYEE_PREFIX_EXISTS", "This employee ID prefix already exists.");
        }

        LoginIdentity identity = loginIdentityRepository.save(
                new LoginIdentity(passwordEncoder.encode(request.password()))
        );
        TenantProvisioningService.ProvisionedTenant provisioned = tenantProvisioningService.provision(
                companyCode,
                request.businessName(),
                prefix,
                identity,
                request.fullName(),
                request.phoneE164(),
                null,
                null,
                null,
                null
        );

        setupCodeService.invalidate();
        log.info("EastApp initial setup completed businessCode={} employeeId={}",
                provisioned.tenant().getCompanyCode(), provisioned.owner().getEmployeeId());
        return new CompleteInitialSetupResponse(
                provisioned.tenant().getCompanyCode(),
                provisioned.tenant().getBusinessName(),
                provisioned.owner().getEmployeeId()
        );
    }

    private boolean isSetupRequired() {
        return loginIdentityRepository.count() == 0;
    }
}
