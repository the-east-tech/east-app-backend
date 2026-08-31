package com.eastapp.backend.activity.service;

import com.eastapp.backend.activity.PushDevice;
import com.eastapp.backend.activity.PushDeviceRepository;
import com.eastapp.backend.activity.UserNotification;
import com.eastapp.backend.activity.UserNotificationRepository;
import com.eastapp.backend.activity.api.NotificationResponse;
import com.eastapp.backend.activity.api.RegisterPushDeviceRequest;
import com.eastapp.backend.activity.api.UnreadCountResponse;
import com.eastapp.backend.auth.security.AuthenticatedUser;
import com.eastapp.backend.common.api.PageResponse;
import com.eastapp.backend.common.error.ApiException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class NotificationService {
    private final UserNotificationRepository notificationRepository;
    private final PushDeviceRepository deviceRepository;

    public NotificationService(
            UserNotificationRepository notificationRepository,
            PushDeviceRepository deviceRepository
    ) {
        this.notificationRepository = notificationRepository;
        this.deviceRepository = deviceRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(AuthenticatedUser principal, int page, int size) {
        return PageResponse.from(
                notificationRepository
                        .findAllByTenantIdAndRecipientUserIdAndDismissedAtIsNullOrderByCreatedAtDescIdDesc(
                                principal.tenantId(),
                                principal.userId(),
                                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100))
                        ),
                NotificationResponse::from
        );
    }

    @Transactional(readOnly = true)
    public UnreadCountResponse unreadCount(AuthenticatedUser principal) {
        return new UnreadCountResponse(
                notificationRepository
                        .countByTenantIdAndRecipientUserIdAndReadAtIsNullAndDismissedAtIsNull(
                                principal.tenantId(),
                                principal.userId()
                        )
        );
    }

    @Transactional
    public NotificationResponse detail(AuthenticatedUser principal, java.util.UUID notificationId) {
        UserNotification notification = find(principal, notificationId);
        notification.markRead(Instant.now());
        return NotificationResponse.from(notification);
    }

    @Transactional
    public NotificationResponse markRead(AuthenticatedUser principal, java.util.UUID notificationId) {
        UserNotification notification = find(principal, notificationId);
        notification.markRead(Instant.now());
        return NotificationResponse.from(notification);
    }

    @Transactional
    public void dismiss(AuthenticatedUser principal, java.util.UUID notificationId) {
        find(principal, notificationId).dismiss(Instant.now());
    }

    @Transactional
    public void registerDevice(AuthenticatedUser principal, RegisterPushDeviceRequest request) {
        Instant now = Instant.now();
        String token = request.token().trim();
        PushDevice device = deviceRepository.findByToken(token)
                .orElseGet(() -> new PushDevice(
                        principal.tenantId(),
                        principal.userId(),
                        principal.sessionId(),
                        token,
                        request.platform(),
                        now
                ));
        device.reassign(
                principal.tenantId(),
                principal.userId(),
                principal.sessionId(),
                request.platform(),
                now
        );
        deviceRepository.save(device);
    }

    @Transactional
    public void unregisterDevice(AuthenticatedUser principal, String rawToken) {
        String token = rawToken == null ? "" : rawToken.trim();
        deviceRepository.findByToken(token)
                .filter(device -> device.getTenantId().equals(principal.tenantId()))
                .filter(device -> device.getUserId().equals(principal.userId()))
                .filter(device -> device.getSessionId().equals(principal.sessionId()))
                .ifPresent(PushDevice::deactivate);
    }

    private UserNotification find(AuthenticatedUser principal, java.util.UUID notificationId) {
        return notificationRepository
                .findByIdAndTenantIdAndRecipientUserIdAndDismissedAtIsNull(
                        notificationId,
                        principal.tenantId(),
                        principal.userId()
                )
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "NOTIFICATION_NOT_FOUND",
                        "Notification not found."
                ));
    }
}
