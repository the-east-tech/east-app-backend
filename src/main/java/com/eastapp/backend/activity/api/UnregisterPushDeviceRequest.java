package com.eastapp.backend.activity.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UnregisterPushDeviceRequest(
        @NotBlank @Size(max = 2048) String token
) {
}
