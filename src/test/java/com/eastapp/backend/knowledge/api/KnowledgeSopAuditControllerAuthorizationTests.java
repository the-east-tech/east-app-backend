package com.eastapp.backend.knowledge.api;

import com.eastapp.backend.auth.security.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class KnowledgeSopAuditControllerAuthorizationTests {
    private static final String AUDIT_PERMISSION =
            "hasAuthority('PERMISSION_KNOWLEDGE_AUDIT_VIEW')";

    @Test
    void auditReadsRequireTheDedicatedHeadAndOwnerPermission() throws NoSuchMethodException {
        assertEquals(
                AUDIT_PERMISSION,
                method("forUser", AuthenticatedUser.class, UUID.class)
                        .getAnnotation(PreAuthorize.class)
                        .value()
        );
        assertEquals(
                AUDIT_PERMISSION,
                method("impact", AuthenticatedUser.class)
                        .getAnnotation(PreAuthorize.class)
                        .value()
        );
    }

    @Test
    void playbackHeartbeatIsAvailableToEveryAuthenticatedRole() throws NoSuchMethodException {
        assertNull(method(
                "record",
                AuthenticatedUser.class,
                UUID.class,
                RecordSopWatchTimeRequest.class
        ).getAnnotation(PreAuthorize.class));
    }

    private Method method(String name, Class<?>... parameterTypes) throws NoSuchMethodException {
        return KnowledgeSopAuditController.class.getDeclaredMethod(name, parameterTypes);
    }
}
