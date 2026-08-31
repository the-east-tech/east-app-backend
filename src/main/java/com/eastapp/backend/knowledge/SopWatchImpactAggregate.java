package com.eastapp.backend.knowledge;

import java.time.Instant;
import java.util.UUID;

public interface SopWatchImpactAggregate {
    UUID getSopId();
    UUID getLinkGroupId();
    String getTitle();
    String getLanguage();
    Long getTotalPlayedSeconds();
    Long getUniqueViewers();
    Instant getLastWatchedAt();
}
