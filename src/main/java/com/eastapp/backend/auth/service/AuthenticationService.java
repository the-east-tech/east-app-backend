package com.eastapp.backend.auth.service;

import com.eastapp.backend.auth.security.AuthenticatedUser;
import com.eastapp.backend.auth.security.SessionTokenService;
import com.eastapp.backend.organisation.Tenant;
import com.eastapp.backend.people.UserAccount;
import com.eastapp.backend.people.UserAccountRepository;
import com.eastapp.backend.auth.UserSession;
import com.eastapp.backend.auth.UserSessionRepository;
import com.eastapp.backend.auth.api.CurrentUserResponse;
import com.eastapp.backend.auth.api.LoginRequest;
import com.eastapp.backend.auth.api.LoginResponse;
import com.eastapp.backend.common.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
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

        if (!passwordEncoder.matches(
                request.password(),
                user.getIdentity().getPasswordHash()
        )) {
            throw invalidCredentials();
        }

        assertLoginAllowed(user);

        SessionTokenService.GeneratedSessionToken generatedToken = sessionTokenService.generate();
        userSessionRepository.save(
                new UserSession(user.getIdentity(), user, generatedToken.tokenHash())
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

        return authenticatedUser(session, user);
    }

    @Transactional(readOnly = true)
    public CurrentUserResponse currentUser(AuthenticatedUser principal) {
        UserAccount user = userAccountRepository
                .findByIdAndTenant_Id(principal.userId(), principal.tenantId())
                .orElseThrow(AuthenticationService::invalidSession);
        assertLoginAllowed(user);
        return CurrentUserResponse.from(user);
    }

    @Transactional(readOnly = true)
    public List<CurrentUserResponse> contexts(AuthenticatedUser principal) {
        UserSession session = currentSession(principal.sessionId());
        return userAccountRepository.findAllContexts(session.getIdentity().getId()).stream()
                .filter(AuthenticationService::isLoginAllowed)
                .map(CurrentUserResponse::from)
                .toList();
    }

    @Transactional
    public CurrentUserResponse switchContext(
            AuthenticatedUser principal,
            UUID targetUserId
    ) {
        UserSession session = currentSession(principal.sessionId());
        UserAccount target = userAccountRepository
                .findByIdAndIdentity_Id(targetUserId, session.getIdentity().getId())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.FORBIDDEN,
                        "CONTEXT_ACCESS_DENIED",
                        "This business context is not assigned to this login."
                ));
        assertLoginAllowed(target);
        session.switchContext(target);
        return CurrentUserResponse.from(target);
    }

    @Transactional
    public void logout(UUID sessionId) {
        UserSession session = currentSession(sessionId);
        if (!session.isRevoked()) {
            session.revoke(Instant.now());
        }
    }

    private UserSession currentSession(UUID sessionId) {
        return userSessionRepository.findByIdAndRevokedAtIsNull(sessionId)
                .orElseThrow(AuthenticationService::invalidSession);
    }

    private static AuthenticatedUser authenticatedUser(
            UserSession session,
            UserAccount user
    ) {
        return new AuthenticatedUser(
                session.getId(),
                user.getId(),
                user.getTenant().getId(),
                user.getRole().getId(),
                user.getEmployeeId(),
                user.getFullName(),
                user.getTenant().getCompanyCode(),
                user.getTenant().getBusinessName(),
                user.getRole().getSystemKey()
        );
    }

    private static void assertLoginAllowed(UserAccount user) {
        if (!isLoginAllowed(user)) {
            throw invalidCredentials();
        }
    }

    private static boolean isLoginAllowed(UserAccount user) {
        return user.getIdentity().isActive()
                && user.getTenant().isActive()
                && user.isActive()
                && user.getRole().isActive();
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
