package com.eastapp.backend.identity.bootstrap;

import com.eastapp.backend.identity.LoginIdentity;
import com.eastapp.backend.identity.LoginIdentityRepository;
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

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class IdentityBootstrapService {

    private static final Map<SystemRole, String> DEFAULT_ROLE_NAMES = roleNames();

    private final TenantRepository tenantRepository;
    private final LoginIdentityRepository loginIdentityRepository;
    private final RoleRepository roleRepository;
    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;

    public IdentityBootstrapService(
            TenantRepository tenantRepository,
            LoginIdentityRepository loginIdentityRepository,
            RoleRepository roleRepository,
            UserAccountRepository userAccountRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.tenantRepository = tenantRepository;
        this.loginIdentityRepository = loginIdentityRepository;
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

        Map<SystemRole, Role> roles = ensureRoles(tenant);
        UserCreation primary = ensureHead(
                tenant,
                roles.get(SystemRole.HEAD),
                properties.getEmployeeId(),
                properties.getFullName(),
                properties.getPhoneE164(),
                properties.getPassword()
        );

        List<UserAccount> owners = new ArrayList<>();
        owners.add(primary.user());
        boolean secondaryCreated = false;
        if (properties.isSecondaryHeadEnabled()) {
            validateSecondaryHead(properties);
            UserCreation secondary = ensureHead(
                    tenant,
                    roles.get(SystemRole.HEAD),
                    properties.getSecondaryHeadEmployeeId(),
                    properties.getSecondaryHeadFullName(),
                    properties.getSecondaryHeadPhoneE164(),
                    properties.getSecondaryHeadPassword()
            );
            owners.add(secondary.user());
            secondaryCreated = secondary.created();
        }

        int contextProfilesCreated = grantContexts(owners);
        return new BootstrapResult(
                tenant.getCompanyCode(),
                primary.user().getEmployeeId(),
                primary.created(),
                secondaryCreated,
                contextProfilesCreated
        );
    }

    private Map<SystemRole, Role> ensureRoles(Tenant tenant) {
        Map<SystemRole, Role> roles = new EnumMap<>(SystemRole.class);
        DEFAULT_ROLE_NAMES.forEach((systemRole, name) -> {
            Role role = roleRepository
                    .findByTenant_IdAndSystemKey(tenant.getId(), systemRole)
                    .orElseGet(() -> roleRepository.save(
                            new Role(tenant, systemRole, name)
                    ));
            roles.put(systemRole, role);
        });
        return roles;
    }

    private UserCreation ensureHead(
            Tenant tenant,
            Role headRole,
            String employeeIdValue,
            String fullName,
            String phoneE164,
            String password
    ) {
        String employeeId = UserAccount.normaliseEmployeeId(employeeIdValue);
        return userAccountRepository.findByTenant_IdAndEmployeeId(tenant.getId(), employeeId)
                .map(existing -> new UserCreation(existing, false))
                .orElseGet(() -> {
                    LoginIdentity identity = loginIdentityRepository.save(
                            new LoginIdentity(passwordEncoder.encode(password))
                    );
                    UserAccount user = userAccountRepository.save(new UserAccount(
                            tenant,
                            identity,
                            employeeId,
                            fullName,
                            phoneE164,
                            headRole
                    ));
                    return new UserCreation(user, true);
                });
    }

    private int grantContexts(List<UserAccount> owners) {
        int created = 0;
        for (Tenant targetTenant : tenantRepository.findAll()) {
            if (!targetTenant.isActive()) {
                continue;
            }
            Role targetHeadRole = roleRepository
                    .findByTenant_IdAndSystemKey(targetTenant.getId(), SystemRole.HEAD)
                    .filter(Role::isActive)
                    .orElse(null);
            if (targetHeadRole == null) {
                continue;
            }
            for (UserAccount owner : owners) {
                if (userAccountRepository.existsByIdentity_IdAndTenant_Id(
                        owner.getIdentity().getId(), targetTenant.getId()
                )) {
                    continue;
                }
                if (userAccountRepository.existsByTenant_IdAndEmployeeId(
                        targetTenant.getId(), owner.getEmployeeId()
                )) {
                    throw new IllegalStateException(
                            "Employee ID " + owner.getEmployeeId()
                                    + " already belongs to another login in tenant "
                                    + targetTenant.getCompanyCode()
                    );
                }
                UserAccount context = new UserAccount(
                        targetTenant,
                        owner.getIdentity(),
                        owner.getEmployeeId(),
                        owner.getFullName(),
                        owner.getPhoneE164(),
                        targetHeadRole
                );
                context.updateProfile(
                        owner.getFullName(),
                        owner.getPhoneE164(),
                        owner.getProfilePhotoKey(),
                        owner.getBirthDate(),
                        owner.getStartDate(),
                        owner.getEndDate()
                );
                userAccountRepository.save(context);
                created++;
            }
        }
        return created;
    }

    private static void validate(BootstrapProperties properties) {
        requireText(properties.getCompanyCode(), "EASTAPP_BOOTSTRAP_COMPANY_CODE");
        requireText(properties.getCompanyName(), "EASTAPP_BOOTSTRAP_COMPANY_NAME");
        requireText(properties.getEmployeeId(), "EASTAPP_BOOTSTRAP_EMPLOYEE_ID");
        requireText(properties.getFullName(), "EASTAPP_BOOTSTRAP_FULL_NAME");
        UserAccount.normalisePhone(
                requireText(properties.getPhoneE164(), "EASTAPP_BOOTSTRAP_PHONE_E164")
        );
        validatePassword(properties.getPassword(), "EASTAPP_BOOTSTRAP_PASSWORD");
    }

    private static void validateSecondaryHead(BootstrapProperties properties) {
        requireText(
                properties.getSecondaryHeadEmployeeId(),
                "EASTAPP_BOOTSTRAP_SECONDARY_HEAD_EMPLOYEE_ID"
        );
        requireText(
                properties.getSecondaryHeadFullName(),
                "EASTAPP_BOOTSTRAP_SECONDARY_HEAD_FULL_NAME"
        );
        UserAccount.normalisePhone(requireText(
                properties.getSecondaryHeadPhoneE164(),
                "EASTAPP_BOOTSTRAP_SECONDARY_HEAD_PHONE_E164"
        ));
        validatePassword(
                properties.getSecondaryHeadPassword(),
                "EASTAPP_BOOTSTRAP_SECONDARY_HEAD_PASSWORD"
        );
    }

    private static void validatePassword(String password, String variableName) {
        String value = requireText(password, variableName);
        if (value.length() < 4) {
            throw new IllegalStateException(variableName + " must contain at least 4 characters");
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

    private record UserCreation(UserAccount user, boolean created) {
    }

    public record BootstrapResult(
            String companyCode,
            String employeeId,
            boolean userCreated,
            boolean secondaryHeadCreated,
            int contextProfilesCreated
    ) {
    }
}
