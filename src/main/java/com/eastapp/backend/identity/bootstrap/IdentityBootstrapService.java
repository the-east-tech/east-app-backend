package com.eastapp.backend.identity.bootstrap;

import com.eastapp.backend.identity.Role;
import com.eastapp.backend.identity.RoleRepository;
import com.eastapp.backend.identity.SystemRole;
import com.eastapp.backend.identity.Tenant;
import com.eastapp.backend.identity.TenantRepository;
import com.eastapp.backend.identity.UserAccount;
import com.eastapp.backend.identity.UserAccountRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.Map;

@Service
public class IdentityBootstrapService {

    private static final Map<SystemRole, String> DEFAULT_ROLE_NAMES = roleNames();

    private final TenantRepository tenantRepository;
    private final RoleRepository roleRepository;
    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;

    public IdentityBootstrapService(
            TenantRepository tenantRepository,
            RoleRepository roleRepository,
            UserAccountRepository userAccountRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.tenantRepository = tenantRepository;
        this.roleRepository = roleRepository;
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public BootstrapResult bootstrap(BootstrapProperties properties) {
        validate(properties);

        String companyCode = Tenant.normaliseCode(properties.getCompanyCode());
        Tenant tenant = tenantRepository.findByCompanyCode(companyCode)
                .orElseGet(() -> tenantRepository.save(
                        new Tenant(companyCode, properties.getCompanyName())
                ));

        Map<SystemRole, Role> roles = new EnumMap<>(SystemRole.class);
        DEFAULT_ROLE_NAMES.forEach((systemRole, name) -> {
            Role role = roleRepository
                    .findByTenant_IdAndSystemKey(tenant.getId(), systemRole)
                    .orElseGet(() -> roleRepository.save(
                            new Role(tenant, systemRole, name)
                    ));
            roles.put(systemRole, role);
        });

        String employeeId = UserAccount.normaliseEmployeeId(properties.getEmployeeId());
        boolean userCreated = false;
        if (!userAccountRepository.existsByTenant_IdAndEmployeeId(tenant.getId(), employeeId)) {
            UserAccount head = new UserAccount(
                    tenant,
                    employeeId,
                    passwordEncoder.encode(properties.getPassword()),
                    properties.getFullName(),
                    properties.getPhoneE164(),
                    roles.get(SystemRole.HEAD)
            );
            userAccountRepository.save(head);
            userCreated = true;
        }

        return new BootstrapResult(tenant.getCompanyCode(), employeeId, userCreated);
    }

    private static void validate(BootstrapProperties properties) {
        requireText(properties.getCompanyCode(), "EASTAPP_BOOTSTRAP_COMPANY_CODE");
        requireText(properties.getCompanyName(), "EASTAPP_BOOTSTRAP_COMPANY_NAME");
        requireText(properties.getEmployeeId(), "EASTAPP_BOOTSTRAP_EMPLOYEE_ID");
        requireText(properties.getFullName(), "EASTAPP_BOOTSTRAP_FULL_NAME");
        UserAccount.normalisePhone(
                requireText(properties.getPhoneE164(), "EASTAPP_BOOTSTRAP_PHONE_E164")
        );
        String password = requireText(
                properties.getPassword(),
                "EASTAPP_BOOTSTRAP_PASSWORD"
        );
        if (password.length() < 4) {
            throw new IllegalStateException(
                    "EASTAPP_BOOTSTRAP_PASSWORD must contain at least 4 characters"
            );
        }
    }

    private static String requireText(String value, String variableName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(variableName + " must be configured");
        }
        return value.trim();
    }

    private static Map<SystemRole, String> roleNames() {
        EnumMap<SystemRole, String> values = new EnumMap<>(SystemRole.class);
        values.put(SystemRole.HEAD, "Head");
        values.put(SystemRole.MANAGER, "Manager");
        values.put(SystemRole.SUPERVISOR, "Supervisor");
        values.put(SystemRole.STAFF_1, "Staff1");
        values.put(SystemRole.STAFF_2, "Staff2");
        return Map.copyOf(values);
    }

    public record BootstrapResult(
            String companyCode,
            String employeeId,
            boolean userCreated
    ) {
    }
}
