package com.eastapp.backend.advertising.api;

import java.time.Instant;
import java.util.List;

public record ActiveAdvertisementFeedResponse(
        List<AdvertisementResponse> advertisements,
        Instant serverTime,
        Instant nextChangeAt
) {
}
