package com.eastapp.backend.identity.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateRoleRequest(
        @NotBlank
        @Size(max = 80)
        String name
) {
}
