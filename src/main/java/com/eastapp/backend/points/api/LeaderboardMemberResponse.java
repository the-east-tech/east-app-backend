package com.eastapp.backend.points.api;

import java.util.UUID;

public record LeaderboardMemberResponse(
        UUID userId,
        String employeeId,
        String fullName,
        String roleName,
        long totalPoints,
        int rank,
        boolean currentUser
) {}
