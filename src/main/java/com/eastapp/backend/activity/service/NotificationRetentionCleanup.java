package com.eastapp.backend.activity.service;

import com.eastapp.backend.activity.UserNotificationRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Component
public class NotificationRetentionCleanup {
    private static final Duration RETENTION = Duration.ofDays(10);

    private final UserNotificationRepository notificationRepository;

    public NotificationRetentionCleanup(UserNotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void deleteExpiredNotifications() {
        notificationRepository.deleteCreatedBefore(Instant.now().minus(RETENTION));
    }
}
