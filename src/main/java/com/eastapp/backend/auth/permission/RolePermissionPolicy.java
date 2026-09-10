package com.eastapp.backend.auth.permission;

import com.eastapp.backend.people.SystemRole;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Code-managed RBAC policy. Roles receive no permissions unless they are
 * explicitly listed here. Admin and Owner are deliberate superuser roles.
 */
public final class RolePermissionPolicy {
    private static final Map<SystemRole, Set<SystemPermission>> GRANTS = grants();

    private RolePermissionPolicy() {
    }

    public static Set<SystemPermission> grantedTo(SystemRole role) {
        if (role == null) {
            return Set.of();
        }
        return GRANTS.getOrDefault(role, Set.of());
    }

    public static boolean allows(SystemRole role, SystemPermission permission) {
        return permission != null && grantedTo(role).contains(permission);
    }

    private static Map<SystemRole, Set<SystemPermission>> grants() {
        EnumMap<SystemRole, Set<SystemPermission>> grants = new EnumMap<>(SystemRole.class);

        Set<SystemPermission> allPermissions = immutable(EnumSet.allOf(SystemPermission.class));
        grants.put(SystemRole.ADMIN, allPermissions);
        grants.put(SystemRole.OWNER, allPermissions);
        grants.put(
                SystemRole.HEAD,
                headManagementPermissions()
        );
        grants.put(
                SystemRole.MANAGER,
                fullManagementPermissions()
        );
        grants.put(
                SystemRole.SUPERVISOR,
                immutable(EnumSet.of(
                        SystemPermission.REPORT_OPERATIONS_ACCESS,
                        SystemPermission.TASK_VIEW,
                        SystemPermission.TASK_CONTRIBUTE
                ))
        );
        Set<SystemPermission> staffPermissions = immutable(EnumSet.of(
                SystemPermission.TASK_VIEW,
                SystemPermission.TASK_CONTRIBUTE
        ));
        grants.put(SystemRole.SENIOR_STAFF, staffPermissions);
        grants.put(SystemRole.STAFF, staffPermissions);
        grants.put(SystemRole.PART_TIME, staffPermissions);

        return Collections.unmodifiableMap(grants);
    }

    private static Set<SystemPermission> immutable(EnumSet<SystemPermission> permissions) {
        return Collections.unmodifiableSet(permissions);
    }

    private static Set<SystemPermission> fullManagementPermissions() {
        return immutable(EnumSet.of(
                SystemPermission.REPORT_INTELLIGENCE_VIEW,
                SystemPermission.REPORT_OPERATIONS_ACCESS,
                SystemPermission.SALES_REPORT_ACCESS,
                SystemPermission.REPORT_REVIEW,
                SystemPermission.TASK_VIEW,
                SystemPermission.TASK_CONTRIBUTE,
                SystemPermission.TASK_VIEW_ALL,
                SystemPermission.TASK_MANAGE,
                SystemPermission.TASK_RATE
        ));
    }

    private static Set<SystemPermission> headManagementPermissions() {
        EnumSet<SystemPermission> permissions = EnumSet.copyOf(fullManagementPermissions());
        permissions.add(SystemPermission.KNOWLEDGE_AUDIT_VIEW);
        return immutable(permissions);
    }
}
