package com.eastapp.backend.people.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateRoleRequest(
        @NotBlank
        @Size(max = 80)
        String name,

        @NotNull
        Boolean active
) {
}
