package com.eastapp.backend.people.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public record UpdateUserRequest(
        @NotBlank
        @Size(max = 120)
        String fullName,

        @NotBlank
        @Size(max = 24)
        String phoneE164,

        @NotNull
        UUID roleId,

        @NotNull
        Boolean active,

        @Size(max = 255)
        String profilePhotoKey,

        LocalDate birthDate,
        LocalDate startDate,
        LocalDate endDate
) {
}
