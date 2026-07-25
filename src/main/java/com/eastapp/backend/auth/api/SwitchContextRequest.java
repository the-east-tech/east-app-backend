package com.eastapp.backend.auth.api;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record SwitchContextRequest(
        @NotNull UUID userId
) {
}
