package com.eastapp.backend.tasks.service;

import com.eastapp.backend.auth.permission.RolePermissionPolicy;
import com.eastapp.backend.auth.permission.SystemPermission;
import com.eastapp.backend.people.SystemRole;
import org.springframework.stereotype.Component;

@Component
public class TaskAccessPolicy {
    public boolean canOversee(SystemRole role) {
        return RolePermissionPolicy.allows(role, SystemPermission.TASK_VIEW_ALL);
    }

    public boolean canRate(SystemRole reviewerRole, SystemRole submitterRole) {
        if (!RolePermissionPolicy.allows(reviewerRole, SystemPermission.TASK_RATE)) {
            return false;
        }
        if (reviewerRole == SystemRole.OWNER) return true;
        return submitterRole != null && reviewerRole.rank() < submitterRole.rank();
    }
}
