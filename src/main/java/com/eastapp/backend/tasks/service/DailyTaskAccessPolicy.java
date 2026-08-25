package com.eastapp.backend.tasks.service;

import com.eastapp.backend.people.SystemRole;
import org.springframework.stereotype.Component;

@Component
public class DailyTaskAccessPolicy {
    public boolean canOversee(SystemRole role) {
        return role == SystemRole.OWNER || role == SystemRole.HEAD || role == SystemRole.MANAGER;
    }

    public boolean canRate(SystemRole reviewerRole, SystemRole submitterRole) {
        if (reviewerRole == SystemRole.OWNER) return true;
        if (reviewerRole != SystemRole.HEAD && reviewerRole != SystemRole.MANAGER) return false;
        return submitterRole != null && reviewerRole.rank() < submitterRole.rank();
    }
}
