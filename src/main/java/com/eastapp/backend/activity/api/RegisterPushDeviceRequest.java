package com.eastapp.backend.activity.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterPushDeviceRequest(
        @NotBlank @Size(max = 2048) String token,
        @NotBlank @Pattern(regexp = "ANDROID|IOS") String platform
) {
}
