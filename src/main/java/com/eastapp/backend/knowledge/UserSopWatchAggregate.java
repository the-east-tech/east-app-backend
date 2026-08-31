package com.eastapp.backend.knowledge;

import java.time.Instant;
import java.util.UUID;

public interface UserSopWatchAggregate {
    UUID getSopId();
    UUID getLinkGroupId();
    String getTitle();
    String getLanguage();
    Long getTotalPlayedSeconds();
    Instant getLastWatchedAt();
}
