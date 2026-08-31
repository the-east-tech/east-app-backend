package com.eastapp.backend.activity.service;

import com.eastapp.backend.activity.ActivityEvent;
import com.eastapp.backend.activity.ActivityEventRepository;
import com.eastapp.backend.activity.PushDevice;
import com.eastapp.backend.activity.PushDeviceRepository;
import com.eastapp.backend.activity.PushOutbox;
import com.eastapp.backend.activity.PushOutboxRepository;
import com.eastapp.backend.activity.UserNotification;
import com.eastapp.backend.activity.UserNotificationRepository;
import com.eastapp.backend.activity.api.ActivityEventResponse;
import com.eastapp.backend.activity.config.NotificationProperties;
import com.eastapp.backend.auth.security.AuthenticatedUser;
import com.eastapp.backend.common.api.PageResponse;
import com.eastapp.backend.people.UserAccount;
import com.eastapp.backend.people.UserAccountRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ActivityService {
    private final ActivityEventRepository eventRepository;
    private final UserNotificationRepository notificationRepository;
    private final PushDeviceRepository deviceRepository;
    private final PushOutboxRepository outboxRepository;
    private final UserAccountRepository userRepository;
    private final NotificationProperties notificationProperties;

    public ActivityService(
            ActivityEventRepository eventRepository,
            UserNotificationRepository notificationRepository,
            PushDeviceRepository deviceRepository,
            PushOutboxRepository outboxRepository,
            UserAccountRepository userRepository,
            NotificationProperties notificationProperties
    ) {
        this.eventRepository = eventRepository;
        this.notificationRepository = notificationRepository;
        this.deviceRepository = deviceRepository;
        this.outboxRepository = outboxRepository;
        this.userRepository = userRepository;
        this.notificationProperties = notificationProperties;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            AuthenticatedUser actor,
            String module,
            String action,
            String entityType,
            String subject,
            String detail,
            UUID targetId,
            String route
    ) {
        ActivityEvent event = eventRepository.saveAndFlush(new ActivityEvent(
                actor.tenantId(),
                actor.userId(),
                actor.fullName(),
                actor.employeeId(),
                actor.systemRole().name(),
                module,
                action,
                entityType,
                subject,
                detail,
                targetId,
                route
        ));

        List<UserAccount> recipients = userRepository
                .findAllByTenant_IdAndActiveTrueOrderByIdentity_FullNameAsc(actor.tenantId())
                .stream()
                .filter(user -> !user.getId().equals(actor.userId()))
                .toList();
        if (recipients.isEmpty()) return;

        List<UserNotification> notifications = recipients.stream()
                .map(user -> new UserNotification(actor.tenantId(), user.getId(), event))
                .toList();
        notificationRepository.saveAllAndFlush(notifications);

        if (!notificationProperties.canQueuePush()) return;
        Map<UUID, UserNotification> notificationsByUser = notifications.stream()
                .collect(Collectors.toMap(UserNotification::getRecipientUserId, Function.identity()));
        List<PushDevice> devices = deviceRepository
                .findAllByTenantIdAndUserIdInAndActiveTrue(
                        actor.tenantId(),
                        notificationsByUser.keySet()
                );
        Instant now = Instant.now();
        List<PushOutbox> deliveries = new ArrayList<>();
        for (PushDevice device : devices) {
            UserNotification notification = notificationsByUser.get(device.getUserId());
            if (notification != null) deliveries.add(new PushOutbox(notification, device, now));
        }
        if (!deliveries.isEmpty()) outboxRepository.saveAll(deliveries);
    }

    @Transactional(readOnly = true)
    public PageResponse<ActivityEventResponse> recent(AuthenticatedUser principal, int page, int size) {
        return PageResponse.from(
                eventRepository.findAllByTenantIdOrderByOccurredAtDescIdDesc(
                        principal.tenantId(),
                        PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100))
                ),
                ActivityEventResponse::from
        );
    }
}
