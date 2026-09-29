package com.eastapp.backend.activity.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
class WorkflowActivityListener {
    private static final Logger log = LoggerFactory.getLogger(WorkflowActivityListener.class);

    private final ActivityService activityService;

    WorkflowActivityListener(ActivityService activityService) {
        this.activityService = activityService;
    }

    @Async("activityTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void record(WorkflowActivityRequest request) {
        try {
            activityService.record(
                    request.actor(),
                    request.module(),
                    request.action(),
                    request.entityType(),
                    request.subject(),
                    request.detail(),
                    request.targetId(),
                    request.route()
            );
        } catch (RuntimeException exception) {
            log.warn(
                    "Unable to record best-effort workflow activity for module={} targetId={}",
                    request.module(),
                    request.targetId(),
                    exception
            );
        }
    }
}
