package com.eastapp.backend.knowledge.service;

import com.eastapp.backend.auth.security.AuthenticatedUser;
import com.eastapp.backend.common.error.ApiException;
import com.eastapp.backend.knowledge.KnowledgeSop;
import com.eastapp.backend.knowledge.KnowledgeSopRepository;
import com.eastapp.backend.knowledge.KnowledgeSopWatchSession;
import com.eastapp.backend.knowledge.KnowledgeSopWatchSessionRepository;
import com.eastapp.backend.knowledge.api.RecordSopWatchTimeRequest;
import com.eastapp.backend.organisation.Tenant;
import com.eastapp.backend.organisation.TenantRepository;
import com.eastapp.backend.people.Role;
import com.eastapp.backend.people.SystemRole;
import com.eastapp.backend.people.UserAccount;
import com.eastapp.backend.people.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeSopAuditServiceTests {
    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID SOP_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID SESSION_ID = UUID.fromString("00000000-0000-0000-0000-000000000004");

    @Mock
    private KnowledgeSopWatchSessionRepository watchRepository;
    @Mock
    private KnowledgeSopRepository sopRepository;
    @Mock
    private UserAccountRepository userRepository;
    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private KnowledgeSopWatchSession existing;
    @Mock
    private KnowledgeSop sop;
    @Mock
    private UserAccount user;
    @Mock
    private Tenant tenant;
    @Mock
    private Role role;

    @Test
    void createsAClientIdentifiedSessionForIdempotentCumulativeTracking() {
        when(watchRepository.findLockedById(SESSION_ID)).thenReturn(Optional.empty());
        when(sopRepository.findByIdAndTenant_Id(SOP_ID, TENANT_ID)).thenReturn(Optional.of(sop));
        when(userRepository.findByIdAndTenant_Id(USER_ID, TENANT_ID)).thenReturn(Optional.of(user));
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));

        service().record(principal(SystemRole.STAFF_2), SOP_ID, request(17));

        ArgumentCaptor<KnowledgeSopWatchSession> saved =
                ArgumentCaptor.forClass(KnowledgeSopWatchSession.class);
        verify(watchRepository).save(saved.capture());
        assertEquals(SESSION_ID, saved.getValue().getId());
        assertEquals(17, saved.getValue().getPlayedSeconds());
        assertEquals(sop, saved.getValue().getSop());
        assertEquals(user, saved.getValue().getUser());
    }

    @Test
    void retryUpdatesTheSameSessionWithCumulativeSeconds() {
        when(watchRepository.findLockedById(SESSION_ID)).thenReturn(Optional.of(existing));
        when(existing.getTenant()).thenReturn(tenant);
        when(existing.getUser()).thenReturn(user);
        when(existing.getSop()).thenReturn(sop);
        when(tenant.getId()).thenReturn(TENANT_ID);
        when(user.getId()).thenReturn(USER_ID);
        when(sop.getId()).thenReturn(SOP_ID);

        service().record(principal(SystemRole.STAFF_1), SOP_ID, request(42));

        verify(existing).recordCumulativePlayedSeconds(
                org.mockito.ArgumentMatchers.eq(42L),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void headCannotReadAnOwnerEmployeeAudit() {
        when(userRepository.findByIdAndTenant_IdAndActiveTrue(USER_ID, TENANT_ID))
                .thenReturn(Optional.of(user));
        when(user.getRole()).thenReturn(role);
        when(role.getSystemKey()).thenReturn(SystemRole.OWNER);

        ApiException error = assertThrows(
                ApiException.class,
                () -> service().forUser(principal(SystemRole.HEAD), USER_ID)
        );

        assertEquals("KNOWLEDGE_AUDIT_USER_DENIED", error.getCode());
    }

    @Test
    void inactiveEmployeesAreNotAvailableThroughKnowledgeAudit() {
        when(userRepository.findByIdAndTenant_IdAndActiveTrue(USER_ID, TENANT_ID))
                .thenReturn(Optional.empty());

        ApiException error = assertThrows(
                ApiException.class,
                () -> service().forUser(principal(SystemRole.OWNER), USER_ID)
        );

        assertEquals("USER_NOT_FOUND", error.getCode());
    }

    private KnowledgeSopAuditService service() {
        return new KnowledgeSopAuditService(
                watchRepository,
                sopRepository,
                userRepository,
                tenantRepository
        );
    }

    private static RecordSopWatchTimeRequest request(long seconds) {
        return new RecordSopWatchTimeRequest(SESSION_ID, seconds);
    }

    private static AuthenticatedUser principal(SystemRole systemRole) {
        return new AuthenticatedUser(
                UUID.randomUUID(),
                USER_ID,
                TENANT_ID,
                UUID.randomUUID(),
                "EMP001",
                "Employee",
                "EAST",
                "The East",
                systemRole
        );
    }
}
