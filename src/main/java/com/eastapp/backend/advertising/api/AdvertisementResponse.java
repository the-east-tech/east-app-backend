package com.eastapp.backend.advertising.api;

import java.time.Instant;
import java.util.UUID;

public record AdvertisementResponse(
        UUID id,
        String imageStorageKey,
        Instant startsAt,
        Instant endsAt,
        int displayOrder,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
}
