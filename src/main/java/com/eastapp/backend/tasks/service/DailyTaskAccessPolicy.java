package com.eastapp.backend.tasks.service;

import com.eastapp.backend.auth.permission.RolePermissionPolicy;
import com.eastapp.backend.auth.permission.SystemPermission;
import com.eastapp.backend.people.SystemRole;
import org.springframework.stereotype.Component;

@Component
public class DailyTaskAccessPolicy {
    public boolean canOversee(SystemRole role) {
        return RolePermissionPolicy.allows(role, SystemPermission.DAILY_TASK_VIEW_ALL);
    }

    public boolean canRate(SystemRole reviewerRole, SystemRole submitterRole) {
        if (!RolePermissionPolicy.allows(reviewerRole, SystemPermission.DAILY_TASK_RATE)) {
            return false;
        }
        if (reviewerRole == SystemRole.OWNER) return true;
        return submitterRole != null && reviewerRole.rank() < submitterRole.rank();
    }
}
