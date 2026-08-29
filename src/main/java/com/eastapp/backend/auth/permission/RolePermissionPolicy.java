package com.eastapp.backend.auth.permission;

import com.eastapp.backend.people.SystemRole;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Code-managed RBAC policy. Roles receive no permissions unless they are
 * explicitly listed here. Owner is the deliberate superuser exception.
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

        grants.put(
                SystemRole.OWNER,
                immutable(EnumSet.allOf(SystemPermission.class))
        );
        grants.put(
                SystemRole.HEAD,
                fullManagementPermissions()
        );
        grants.put(
                SystemRole.MANAGER,
                fullManagementPermissions()
        );
        grants.put(
                SystemRole.SUPERVISOR,
                immutable(EnumSet.of(
                        SystemPermission.REPORT_OPERATIONS_ACCESS,
                        SystemPermission.DAILY_TASK_VIEW,
                        SystemPermission.DAILY_TASK_CONTRIBUTE
                ))
        );
        grants.put(
                SystemRole.STAFF_1,
                immutable(EnumSet.of(
                        SystemPermission.DAILY_TASK_VIEW,
                        SystemPermission.DAILY_TASK_CONTRIBUTE
                ))
        );
        grants.put(
                SystemRole.STAFF_2,
                immutable(EnumSet.of(
                        SystemPermission.DAILY_TASK_VIEW,
                        SystemPermission.DAILY_TASK_CONTRIBUTE
                ))
        );

        return Collections.unmodifiableMap(grants);
    }

    private static Set<SystemPermission> immutable(EnumSet<SystemPermission> permissions) {
        return Collections.unmodifiableSet(permissions);
    }

    private static Set<SystemPermission> fullManagementPermissions() {
        return immutable(EnumSet.of(
                SystemPermission.REPORT_INTELLIGENCE_VIEW,
                SystemPermission.REPORT_OPERATIONS_ACCESS,
                SystemPermission.REPORT_REVIEW,
                SystemPermission.DAILY_TASK_VIEW,
                SystemPermission.DAILY_TASK_CONTRIBUTE,
                SystemPermission.DAILY_TASK_VIEW_ALL,
                SystemPermission.DAILY_TASK_MANAGE,
                SystemPermission.DAILY_TASK_RATE
        ));
    }
}
