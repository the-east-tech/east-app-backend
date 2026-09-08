package com.eastapp.backend.activity.service;

import com.eastapp.backend.auth.security.AuthenticatedUser;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
public class WorkflowActivityService {
    private final ActivityService activityService;

    public WorkflowActivityService(ActivityService activityService) {
        this.activityService = activityService;
    }

    public void recordTransition(
            AuthenticatedUser actor,
            String module,
            String entityType,
            UUID targetId,
            String subject,
            Object previousStatus,
            Object nextStatus,
            String route
    ) {
        String previous = status(previousStatus);
        String next = status(nextStatus);
        if (Objects.equals(previous, next)) return;

        activityService.record(
                actor,
                module,
                action(previous, next),
                entityType,
                subject,
                "Workflow status: " + (previous.isEmpty() ? "NEW" : previous) + " -> " + next,
                targetId,
                route
        );
    }

    private static String status(Object value) {
        if (value == null) return "";
        return value.toString().trim().toUpperCase(Locale.ROOT);
    }

    private static String action(String previous, String next) {
        if ("SUBMITTED".equals(next)) return "submitted";
        if ("DONE".equals(next)) return "completed";
        if ("DONE".equals(previous) && "PENDING".equals(next)) return "amended";
        if ("PENDING".equals(next)) return "returned";
        throw new IllegalArgumentException(
                "Unsupported workflow transition: " + previous + " -> " + next
        );
    }
}
