package com.eastapp.backend.activity.tracking;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Objects;
import java.util.UUID;

public final class ActivityEventContext {
    private static final String ATTRIBUTE = ActivityEventContext.class.getName();

    private ActivityEventContext() {
    }

    public static void attach(
            HttpServletRequest request,
            UUID targetId,
            String subject,
            String detail
    ) {
        Objects.requireNonNull(request, "request must not be null")
                .setAttribute(ATTRIBUTE, new Values(targetId, subject, detail));
    }

    static Values from(HttpServletRequest request) {
        Object value = request.getAttribute(ATTRIBUTE);
        return value instanceof Values values ? values : Values.EMPTY;
    }

    record Values(UUID targetId, String subject, String detail) {
        private static final Values EMPTY = new Values(null, "", "");

        Values {
            subject = subject == null ? "" : subject.trim();
            detail = detail == null ? "" : detail.trim();
        }
    }
}
