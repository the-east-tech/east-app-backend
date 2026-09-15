package com.eastapp.backend.activity.service;

import com.eastapp.backend.activity.PushDevice;
import com.eastapp.backend.activity.PushOutbox;
import com.eastapp.backend.activity.PushOutboxRepository;
import com.eastapp.backend.auth.UserSessionRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Component
public class PushOutboxDispatcher {
    private final PushOutboxRepository outboxRepository;
    private final FirebasePushGateway pushGateway;
    private final UserSessionRepository sessionRepository;

    public PushOutboxDispatcher(
            PushOutboxRepository outboxRepository,
            FirebasePushGateway pushGateway,
            UserSessionRepository sessionRepository
    ) {
        this.outboxRepository = outboxRepository;
        this.pushGateway = pushGateway;
        this.sessionRepository = sessionRepository;
    }

    @Scheduled(fixedDelayString = "${eastapp.notifications.dispatch-delay-ms:5000}")
    @Transactional
    public void dispatch() {
        if (!pushGateway.isEnabled()) return;
        Instant now = Instant.now();
        List<PushOutbox> deliveries = outboxRepository.findDue(
                now,
                PageRequest.of(0, 50)
        );
        for (PushOutbox delivery : deliveries) {
            PushDevice device = delivery.getDevice();
            boolean sessionMatches = sessionRepository
                    .findByIdAndRevokedAtIsNull(device.getSessionId())
                    .filter(session -> session.getUserAccount().getId().equals(device.getUserId()))
                    .filter(session -> session.getUserAccount().getTenant().getId().equals(device.getTenantId()))
                    .filter(session -> session.getUserAccount().isActive())
                    .filter(session -> session.getUserAccount().getIdentity().isActive())
                    .filter(session -> session.getUserAccount().getTenant().isActive())
                    .filter(session -> session.getUserAccount().getRole().isActive())
                    .isPresent();
            if (!device.isActive()
                    || !sessionMatches
                    || !device.getTenantId().equals(delivery.getNotification().getTenantId())
                    || !device.getUserId().equals(delivery.getNotification().getRecipientUserId())) {
                delivery.sent(now);
                continue;
            }
            FirebasePushGateway.Result result = pushGateway.send(
                    delivery.getNotification(),
                    device.getToken()
            );
            switch (result) {
                case SENT -> delivery.sent(now);
                case INVALID_TOKEN -> {
                    device.deactivate();
                    delivery.sent(now);
                }
                case RETRYABLE_FAILURE -> delivery.failed(now, "Firebase push delivery failed");
            }
        }
    }
}
