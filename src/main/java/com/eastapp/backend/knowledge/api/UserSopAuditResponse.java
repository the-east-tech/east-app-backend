package com.eastapp.backend.knowledge.api;

import com.eastapp.backend.people.api.UserResponse;

import java.util.List;

public record UserSopAuditResponse(
        UserResponse user,
        long totalPlayedSeconds,
        List<UserSopPlaybackResponse> videos
) {
}
