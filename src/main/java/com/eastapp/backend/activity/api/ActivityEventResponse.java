package com.eastapp.backend.activity.api;

import com.eastapp.backend.activity.ActivityEvent;

import java.time.Instant;
import java.util.UUID;

public record ActivityEventResponse(
        UUID eventId,
        UUID actorUserId,
        String actorName,
        String actorEmployeeId,
        String actorRole,
        String module,
        String action,
        String entityType,
        String subject,
        String detail,
        UUID targetId,
        String summary,
        Instant occurredAt
) {
    public static ActivityEventResponse from(ActivityEvent event) {
        return new ActivityEventResponse(
                event.getId(),
                event.getActorUserId(),
                event.getActorName(),
                event.getActorEmployeeId(),
                event.getActorRole(),
                event.getModule(),
                event.getAction(),
                event.getEntityType(),
                event.getSubject(),
                event.getDetail(),
                event.getTargetId(),
                event.summary(),
                event.getOccurredAt()
        );
    }
}
