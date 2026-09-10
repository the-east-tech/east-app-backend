package com.eastapp.backend.activity.service;

import com.eastapp.backend.activity.ActivityEventRepository;
import com.eastapp.backend.activity.UserNotificationRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Component
public class NotificationRetentionCleanup {
    private static final Duration FEED_RETENTION = Duration.ofDays(30);

    private final UserNotificationRepository notificationRepository;
    private final ActivityEventRepository activityEventRepository;

    public NotificationRetentionCleanup(
            UserNotificationRepository notificationRepository,
            ActivityEventRepository activityEventRepository
    ) {
        this.notificationRepository = notificationRepository;
        this.activityEventRepository = activityEventRepository;
    }

    @Transactional
    public int cleanupExpiredActivityData(Instant now) {
        int notifications = notificationRepository.deleteCreatedBefore(
                now.minus(FEED_RETENTION)
        );
        int events = activityEventRepository.deleteOccurredBefore(
                now.minus(FEED_RETENTION)
        );
        return notifications + events;
    }
}
