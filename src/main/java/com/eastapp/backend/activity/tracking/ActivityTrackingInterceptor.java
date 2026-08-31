package com.eastapp.backend.activity.tracking;

import com.eastapp.backend.activity.service.ActivityService;
import com.eastapp.backend.auth.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;
import java.util.UUID;

@Component
public class ActivityTrackingInterceptor implements HandlerInterceptor {
    private static final Logger log = LoggerFactory.getLogger(ActivityTrackingInterceptor.class);

    private final ActivityService activityService;

    public ActivityTrackingInterceptor(ActivityService activityService) {
        this.activityService = activityService;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception exception
    ) {
        if (exception != null || response.getStatus() < 200 || response.getStatus() >= 300) return;
        if (!(handler instanceof HandlerMethod handlerMethod)) return;

        ActivityTracked tracked = AnnotatedElementUtils.findMergedAnnotation(
                handlerMethod.getMethod(),
                ActivityTracked.class
        );
        if (tracked == null) return;

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser actor)) {
            return;
        }

        try {
            ActivityEventContext.Values context = ActivityEventContext.from(request);
            activityService.record(
                    actor,
                    tracked.module(),
                    tracked.action(),
                    tracked.entity(),
                    context.subject(),
                    context.detail(),
                    context.targetId() == null
                            ? targetId(request, tracked.targetPathVariable())
                            : context.targetId(),
                    request.getRequestURI()
            );
        } catch (RuntimeException activityFailure) {
            log.error(
                    "Business change completed but activity event recording failed: method={} path={} actorUserId={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    actor.userId(),
                    activityFailure
            );
        }
    }

    @SuppressWarnings("unchecked")
    private static UUID targetId(HttpServletRequest request, String configuredVariable) {
        Object value = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (!(value instanceof Map<?, ?> rawVariables)) return null;
        Map<String, String> variables = (Map<String, String>) rawVariables;
        if (!configuredVariable.isBlank()) {
            return parseUuid(variables.get(configuredVariable));
        }
        for (String candidate : variables.values()) {
            UUID parsed = parseUuid(candidate);
            if (parsed != null) return parsed;
        }
        return null;
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
