package com.eastapp.backend.knowledge.api;

import com.eastapp.backend.auth.security.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KnowledgeSopControllerAuthorizationTests {
    private static final String MANAGEMENT_ROLES = "hasAnyRole('OWNER', 'HEAD', 'MANAGER')";

    @Test
    void createUpdateAndBulkDeleteRequireOwnerHeadOrManager() throws NoSuchMethodException {
        assertManagementRoles(method("create", AuthenticatedUser.class, CreateKnowledgeSopRequest.class));
        assertManagementRoles(method(
                "update",
                AuthenticatedUser.class,
                UUID.class,
                UpdateKnowledgeSopRequest.class
        ));
        assertManagementRoles(method(
                "bulkDelete",
                AuthenticatedUser.class,
                BulkDeleteKnowledgeSopsRequest.class
        ));
    }

    private Method method(String name, Class<?>... parameterTypes) throws NoSuchMethodException {
        return KnowledgeSopController.class.getDeclaredMethod(name, parameterTypes);
    }

    private void assertManagementRoles(Method method) {
        assertEquals(MANAGEMENT_ROLES, method.getAnnotation(PreAuthorize.class).value());
    }
}
