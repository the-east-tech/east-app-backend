package com.eastapp.backend.activity.service;

import com.eastapp.backend.auth.security.AuthenticatedUser;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
public class WorkflowActivityService {
    private final ApplicationEventPublisher eventPublisher;

    public WorkflowActivityService(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
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
        recordTransition(actor, module, entityType, targetId, subject,
                previousStatus, nextStatus, route, "");
    }

    public void recordTransition(
            AuthenticatedUser actor,
            String module,
            String entityType,
            UUID targetId,
            String subject,
            Object previousStatus,
            Object nextStatus,
            String route,
            String changes
    ) {
        String previous = status(previousStatus);
        String next = status(nextStatus);
        if (Objects.equals(previous, next)) return;
        String action = action(previous, next);
        if (actor.isAdmin()) return;

        eventPublisher.publishEvent(new WorkflowActivityRequest(
                actor,
                module,
                action,
                entityType,
                subject,
                "Workflow status: " + (previous.isEmpty() ? "NEW" : previous) + " -> " + next
                        + (changes == null || changes.isBlank() ? "" : "; " + changes),
                targetId,
                route
        ));
    }

    public void recordChange(
            AuthenticatedUser actor,
            String module,
            String entityType,
            UUID targetId,
            String subject,
            String route,
            String changes
    ) {
        if (actor.isAdmin()) return;
        eventPublisher.publishEvent(new WorkflowActivityRequest(
                actor, module, "imported", entityType, subject,
                changes == null ? "" : changes, targetId, route
        ));
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
        if ("REJECTED".equals(next)) return "rejected";
        throw new IllegalArgumentException(
                "Unsupported workflow transition: " + previous + " -> " + next
        );
    }
}
