package com.eastapp.backend.activity.api;

import com.eastapp.backend.activity.UserNotification;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID notificationId,
        ActivityEventResponse event,
        Instant readAt,
        Instant createdAt
) {
    public static NotificationResponse from(UserNotification notification) {
        return new NotificationResponse(
                notification.getId(),
                ActivityEventResponse.from(notification.getActivityEvent()),
                notification.getReadAt(),
                notification.getCreatedAt()
        );
    }
}
