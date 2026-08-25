package com.eastapp.backend.setup.api;

import java.time.Instant;

public record SetupStatusResponse(
        boolean setupRequired,
        String setupCode,
        Instant setupCodeExpiresAt
) {
}
