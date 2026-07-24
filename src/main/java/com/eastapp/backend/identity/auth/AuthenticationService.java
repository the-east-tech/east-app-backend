package com.eastapp.backend.identity.auth;

import com.eastapp.backend.identity.Tenant;
import com.eastapp.backend.identity.UserAccount;
import com.eastapp.backend.identity.UserAccountRepository;
import com.eastapp.backend.identity.UserSession;
import com.eastapp.backend.identity.UserSessionRepository;
import com.eastapp.backend.identity.api.CurrentUserResponse;
import com.eastapp.backend.identity.api.LoginRequest;
import com.eastapp.backend.identity.api.LoginResponse;
import com.eastapp.backend.identity.support.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class AuthenticationService {

    private static final Duration LAST_USED_UPDATE_INTERVAL = Duration.ofMinutes(5);

    private final UserAccountRepository userAccountRepository;
    private final UserSessionRepository userSessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final SessionTokenService sessionTokenService;

    public AuthenticationService(
            UserAccountRepository userAccountRepository,
            UserSessionRepository userSessionRepository,
            PasswordEncoder passwordEncoder,
            SessionTokenService sessionTokenService
    ) {
        this.userAccountRepository = userAccountRepository;
        this.userSessionRepository = userSessionRepository;
        this.passwordEncoder = passwordEncoder;
        this.sessionTokenService = sessionTokenService;
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        String companyCode = Tenant.normaliseCode(request.companyCode());
        String employeeId = UserAccount.normaliseEmployeeId(request.employeeId());
        String phoneE164 = UserAccount.normalisePhone(request.phoneE164());

        UserAccount user = userAccountRepository
                .findByTenant_CompanyCodeAndEmployeeIdAndPhoneE164(
                        companyCode,
                        employeeId,
                        phoneE164
                )
                .orElseThrow(AuthenticationService::invalidCredentials);

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw invalidCredentials();
        }

        assertLoginAllowed(user);

        SessionTokenService.GeneratedSessionToken generatedToken = sessionTokenService.generate();
        UserSession session = userSessionRepository.save(
                new UserSession(user, generatedToken.tokenHash())
        );

        return new LoginResponse(
                generatedToken.rawToken(),
                CurrentUserResponse.from(user)
        );
    }

    @Transactional
    public AuthenticatedUser authenticateToken(String rawToken) {
        UserSession session = userSessionRepository
                .findByTokenHashAndRevokedAtIsNull(sessionTokenService.hash(rawToken))
                .orElseThrow(AuthenticationService::invalidSession);

        UserAccount user = session.getUserAccount();
        assertLoginAllowed(user);

        Instant now = Instant.now();
        if (session.getLastUsedAt() == null
                || session.getLastUsedAt().plus(LAST_USED_UPDATE_INTERVAL).isBefore(now)) {
            session.markUsed(now);
        }

        return new AuthenticatedUser(
                session.getId(),
                user.getId(),
                user.getTenant().getId(),
                user.getRole().getId(),
                user.getEmployeeId(),
                user.getFullName(),
                user.getRole().getSystemKey()
        );
    }

    @Transactional(readOnly = true)
    public CurrentUserResponse currentUser(AuthenticatedUser principal) {
        UserAccount user = userAccountRepository
                .findByIdAndTenant_Id(principal.userId(), principal.tenantId())
                .orElseThrow(AuthenticationService::invalidSession);
        assertLoginAllowed(user);
        return CurrentUserResponse.from(user);
    }

    @Transactional
    public void logout(UUID sessionId, UUID userId) {
        UserSession session = userSessionRepository.findById(sessionId)
                .filter(value -> value.getUserAccount().getId().equals(userId))
                .orElseThrow(AuthenticationService::invalidSession);
        if (!session.isRevoked()) {
            session.revoke(Instant.now());
        }
    }

    private static void assertLoginAllowed(UserAccount user) {
        if (!user.getTenant().isActive()
                || !user.isActive()
                || !user.getRole().isActive()) {
            throw invalidCredentials();
        }
    }

    private static ApiException invalidCredentials() {
        return new ApiException(
                HttpStatus.UNAUTHORIZED,
                "INVALID_CREDENTIALS",
                "Company ID, Employee ID, phone number or password is incorrect."
        );
    }

    private static ApiException invalidSession() {
        return new ApiException(
                HttpStatus.UNAUTHORIZED,
                "INVALID_SESSION",
                "The session is invalid or has been revoked."
        );
    }
}
