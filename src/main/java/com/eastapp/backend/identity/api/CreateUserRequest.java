package com.eastapp.backend.identity.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public record CreateUserRequest(
        @NotBlank
        @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9_-]{1,31}$")
        String employeeId,

        @NotBlank
        @Size(min = 4, max = 128)
        String password,

        @NotBlank
        @Size(max = 120)
        String fullName,

        @NotBlank
        @Size(max = 24)
        String phoneE164,

        @NotNull
        UUID roleId,

        @Size(max = 255)
        String profilePhotoKey,

        LocalDate birthDate,
        LocalDate startDate,
        LocalDate endDate
) {
}
