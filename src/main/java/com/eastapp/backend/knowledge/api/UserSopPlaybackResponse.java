package com.eastapp.backend.knowledge.api;

import com.eastapp.backend.knowledge.KnowledgeSopLanguage;
import com.eastapp.backend.knowledge.UserSopWatchAggregate;

import java.time.Instant;
import java.util.UUID;

public record UserSopPlaybackResponse(
        UUID sopId,
        UUID linkGroupId,
        String title,
        KnowledgeSopLanguage language,
        long totalPlayedSeconds,
        Instant lastWatchedAt
) {
    public static UserSopPlaybackResponse from(UserSopWatchAggregate aggregate) {
        return new UserSopPlaybackResponse(
                aggregate.getSopId(),
                aggregate.getLinkGroupId(),
                aggregate.getTitle(),
                KnowledgeSopLanguage.valueOf(aggregate.getLanguage()),
                valueOrZero(aggregate.getTotalPlayedSeconds()),
                aggregate.getLastWatchedAt()
        );
    }

    private static long valueOrZero(Long value) {
        return value == null ? 0 : value;
    }
}
