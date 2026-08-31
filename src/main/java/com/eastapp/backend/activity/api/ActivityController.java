package com.eastapp.backend.activity.api;

import com.eastapp.backend.activity.service.ActivityService;
import com.eastapp.backend.auth.security.AuthenticatedUser;
import com.eastapp.backend.common.api.PageResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/activity")
public class ActivityController {
    private final ActivityService activityService;

    public ActivityController(ActivityService activityService) {
        this.activityService = activityService;
    }

    @GetMapping("/recent")
    PageResponse<ActivityEventResponse> recent(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size
    ) {
        return activityService.recent(principal, page, size);
    }
}
