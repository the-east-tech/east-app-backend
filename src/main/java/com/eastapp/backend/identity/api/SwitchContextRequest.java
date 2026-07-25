package com.eastapp.backend.identity.api;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record SwitchContextRequest(
        @NotNull UUID userId
) {
}
