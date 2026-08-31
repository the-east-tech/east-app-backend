package com.eastapp.backend.knowledge.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record RecordSopWatchTimeRequest(
        @NotNull UUID sessionId,
        @Min(0) @Max(604800) long playedSeconds
) {
}
