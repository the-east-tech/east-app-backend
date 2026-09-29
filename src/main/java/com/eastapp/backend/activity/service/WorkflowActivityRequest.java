package com.eastapp.backend.activity.service;

import com.eastapp.backend.auth.security.AuthenticatedUser;

import java.util.UUID;

record WorkflowActivityRequest(
        AuthenticatedUser actor,
        String module,
        String action,
        String entityType,
        String subject,
        String detail,
        UUID targetId,
        String route
) {
}
