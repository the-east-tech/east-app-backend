package com.eastapp.backend.tasks.service;

import com.eastapp.backend.people.SystemRole;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DailyTaskAccessPolicyTests {
    private final DailyTaskAccessPolicy policy = new DailyTaskAccessPolicy();

    @Test
    void onlyOwnerHeadAndManagerCanOverseeAllTasks() {
        assertTrue(policy.canOversee(SystemRole.OWNER));
        assertTrue(policy.canOversee(SystemRole.HEAD));
        assertTrue(policy.canOversee(SystemRole.MANAGER));
        assertFalse(policy.canOversee(SystemRole.SUPERVISOR));
        assertFalse(policy.canOversee(SystemRole.STAFF_1));
        assertFalse(policy.canOversee(SystemRole.STAFF_2));
    }

    @Test
    void ownerCanRateEverySubmissionIncludingAnOwnerSubmission() {
        for (SystemRole submitter : SystemRole.values()) {
            assertTrue(policy.canRate(SystemRole.OWNER, submitter));
        }
    }

    @Test
    void headCanRateOnlyLowerRankedSubmitters() {
        assertFalse(policy.canRate(SystemRole.HEAD, SystemRole.OWNER));
        assertFalse(policy.canRate(SystemRole.HEAD, SystemRole.HEAD));
        assertTrue(policy.canRate(SystemRole.HEAD, SystemRole.MANAGER));
        assertTrue(policy.canRate(SystemRole.HEAD, SystemRole.STAFF_1));
    }

    @Test
    void managerCanRateStaffButNotManagerHeadOrOwner() {
        assertFalse(policy.canRate(SystemRole.MANAGER, SystemRole.OWNER));
        assertFalse(policy.canRate(SystemRole.MANAGER, SystemRole.HEAD));
        assertFalse(policy.canRate(SystemRole.MANAGER, SystemRole.MANAGER));
        assertTrue(policy.canRate(SystemRole.MANAGER, SystemRole.SUPERVISOR));
        assertTrue(policy.canRate(SystemRole.MANAGER, SystemRole.STAFF_2));
    }

    @Test
    void operationalRolesCannotRateAndMissingSubmitterRoleIsDenied() {
        assertFalse(policy.canRate(SystemRole.SUPERVISOR, SystemRole.STAFF_1));
        assertFalse(policy.canRate(SystemRole.STAFF_1, SystemRole.STAFF_2));
        assertFalse(policy.canRate(SystemRole.HEAD, null));
    }
}
