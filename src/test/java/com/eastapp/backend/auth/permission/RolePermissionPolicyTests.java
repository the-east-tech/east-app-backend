package com.eastapp.backend.auth.permission;

import com.eastapp.backend.auth.security.AuthenticatedUser;
import com.eastapp.backend.people.SystemRole;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RolePermissionPolicyTests {
    @Test
    void ownerAndHeadReceiveKnowledgeAuditWhileManagerDoesNot() {
        Set<SystemPermission> management = EnumSet.of(
                SystemPermission.REPORT_INTELLIGENCE_VIEW,
                SystemPermission.REPORT_OPERATIONS_ACCESS,
                SystemPermission.SALES_REPORT_ACCESS,
                SystemPermission.REPORT_REVIEW,
                SystemPermission.DAILY_TASK_VIEW,
                SystemPermission.DAILY_TASK_CONTRIBUTE,
                SystemPermission.DAILY_TASK_VIEW_ALL,
                SystemPermission.DAILY_TASK_MANAGE,
                SystemPermission.DAILY_TASK_RATE
        );

        assertEquals(
                EnumSet.allOf(SystemPermission.class),
                RolePermissionPolicy.grantedTo(SystemRole.OWNER)
        );
        Set<SystemPermission> head = EnumSet.copyOf(management);
        head.add(SystemPermission.KNOWLEDGE_AUDIT_VIEW);
        assertEquals(head, RolePermissionPolicy.grantedTo(SystemRole.HEAD));
        assertEquals(management, RolePermissionPolicy.grantedTo(SystemRole.MANAGER));
        assertFalse(RolePermissionPolicy.allows(
                SystemRole.MANAGER,
                SystemPermission.KNOWLEDGE_AUDIT_VIEW
        ));
    }

    @Test
    void supervisorReceivesOperationalReportsAndScopedDailyTasksOnly() {
        assertEquals(
                Set.of(
                        SystemPermission.REPORT_OPERATIONS_ACCESS,
                        SystemPermission.DAILY_TASK_VIEW,
                        SystemPermission.DAILY_TASK_CONTRIBUTE
                ),
                RolePermissionPolicy.grantedTo(SystemRole.SUPERVISOR)
        );
    }

    @Test
    void staffRolesReceiveScopedDailyTasksOnly() {
        Set<SystemPermission> expected = Set.of(
                SystemPermission.DAILY_TASK_VIEW,
                SystemPermission.DAILY_TASK_CONTRIBUTE
        );

        assertEquals(expected, RolePermissionPolicy.grantedTo(SystemRole.STAFF_1));
        assertEquals(expected, RolePermissionPolicy.grantedTo(SystemRole.STAFF_2));
    }

    @Test
    void missingRoleOrPermissionFailsClosed() {
        assertTrue(RolePermissionPolicy.grantedTo(null).isEmpty());
        assertFalse(RolePermissionPolicy.allows(null, SystemPermission.DAILY_TASK_VIEW));
        assertFalse(RolePermissionPolicy.allows(SystemRole.OWNER, null));
    }

    @Test
    void authenticatedUserExposesRoleAndPermissionAuthorities() {
        AuthenticatedUser user = new AuthenticatedUser(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "EMP001",
                "Manager",
                "EAST",
                "The East",
                SystemRole.MANAGER
        );

        Set<String> authorities = user.authorities().stream()
                .map(authority -> authority.getAuthority())
                .collect(java.util.stream.Collectors.toSet());

        assertTrue(authorities.contains("ROLE_MANAGER"));
        assertTrue(authorities.contains("PERMISSION_REPORT_INTELLIGENCE_VIEW"));
        assertTrue(authorities.contains("PERMISSION_SALES_REPORT_ACCESS"));
        assertTrue(authorities.contains("PERMISSION_DAILY_TASK_MANAGE"));
    }
}
