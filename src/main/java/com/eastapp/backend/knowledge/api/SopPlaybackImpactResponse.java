package com.eastapp.backend.knowledge.api;

import com.eastapp.backend.knowledge.KnowledgeSopLanguage;
import com.eastapp.backend.knowledge.SopWatchImpactAggregate;

import java.time.Instant;
import java.util.UUID;

public record SopPlaybackImpactResponse(
        UUID sopId,
        UUID linkGroupId,
        String title,
        KnowledgeSopLanguage language,
        long totalPlayedSeconds,
        long uniqueViewers,
        Instant lastWatchedAt
) {
    public static SopPlaybackImpactResponse from(SopWatchImpactAggregate aggregate) {
        return new SopPlaybackImpactResponse(
                aggregate.getSopId(),
                aggregate.getLinkGroupId(),
                aggregate.getTitle(),
                KnowledgeSopLanguage.valueOf(aggregate.getLanguage()),
                valueOrZero(aggregate.getTotalPlayedSeconds()),
                valueOrZero(aggregate.getUniqueViewers()),
                aggregate.getLastWatchedAt()
        );
    }

    private static long valueOrZero(Long value) {
        return value == null ? 0 : value;
    }
}
