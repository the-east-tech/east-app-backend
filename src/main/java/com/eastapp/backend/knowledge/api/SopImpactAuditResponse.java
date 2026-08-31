package com.eastapp.backend.knowledge.api;

import java.util.List;

public record SopImpactAuditResponse(
        long totalPlayedSeconds,
        long uniqueViewers,
        List<SopPlaybackImpactResponse> videos
) {
}
