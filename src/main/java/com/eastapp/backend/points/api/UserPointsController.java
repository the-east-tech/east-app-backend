package com.eastapp.backend.points.api;

import com.eastapp.backend.auth.security.AuthenticatedUser;
import com.eastapp.backend.points.service.UserPointsService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/points")
public class UserPointsController {
    private final UserPointsService userPointsService;

    public UserPointsController(UserPointsService userPointsService) {
        this.userPointsService = userPointsService;
    }

    @GetMapping("/leaderboard")
    LeaderboardResponse leaderboard(
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        return userPointsService.leaderboard(principal);
    }

    @PostMapping("/adjustments")
    @PreAuthorize("hasAnyRole('OWNER', 'HEAD')")
    ResponseEntity<UserPointAdjustmentResponse> adjust(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody AdjustUserPointsRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(userPointsService.adjust(principal, request));
    }
}
