package com.eastapp.backend.activity.service;

import com.eastapp.backend.activity.ActivityEventRepository;
import com.eastapp.backend.activity.UserNotificationRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Component
public class NotificationRetentionCleanup {
    private static final Duration NOTIFICATION_RETENTION = Duration.ofDays(10);
    private static final Duration ACTIVITY_EVENT_RETENTION = Duration.ofDays(30);

    private final UserNotificationRepository notificationRepository;
    private final ActivityEventRepository activityEventRepository;

    public NotificationRetentionCleanup(
            UserNotificationRepository notificationRepository,
            ActivityEventRepository activityEventRepository
    ) {
        this.notificationRepository = notificationRepository;
        this.activityEventRepository = activityEventRepository;
    }

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void deleteExpiredActivityData() {
        cleanupExpiredActivityData(Instant.now());
    }

    public int cleanupExpiredActivityData(Instant now) {
        int notifications = notificationRepository.deleteCreatedBefore(
                now.minus(NOTIFICATION_RETENTION)
        );
        int events = activityEventRepository.deleteOccurredBefore(
                now.minus(ACTIVITY_EVENT_RETENTION)
        );
        return notifications + events;
    }
}
