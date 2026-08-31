package com.eastapp.backend.activity;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ActivityEventTests {
    @Test
    void summaryUsesTheSpecificSubjectWhenAvailable() {
        ActivityEvent event = event("Kitchen closing check", "Task date: 2026-08-31");

        assertEquals("Nicky submitted Kitchen closing check", event.summary());
        assertEquals("Kitchen closing check", event.getSubject());
        assertEquals("Task date: 2026-08-31", event.getDetail());
    }

    @Test
    void optionalDetailIsTrimmedAndBoundedForNotificationStorage() {
        ActivityEvent event = event("  ", " x".repeat(1200));

        assertEquals("Nicky submitted daily task", event.summary());
        assertEquals(2000, event.getDetail().length());
    }

    private static ActivityEvent event(String subject, String detail) {
        return new ActivityEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Nicky",
                "E0001",
                "STAFF_1",
                "Daily Task",
                "submitted",
                "daily task",
                subject,
                detail,
                UUID.randomUUID(),
                "/api/v1/daily-tasks/records/example/submit"
        );
    }
}
