package com.eastapp.backend.points.api;

import java.util.List;

public record LeaderboardResponse(
        long currentUserTotalPoints,
        Integer currentUserRank,
        List<LeaderboardMemberResponse> members
) {}
