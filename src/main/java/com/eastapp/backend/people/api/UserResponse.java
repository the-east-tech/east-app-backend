package com.eastapp.backend.people.api;

import com.eastapp.backend.people.UserAccount;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String employeeId,
        String fullName,
        String phoneE164,
        String profilePhotoKey,
        LocalDate birthDate,
        LocalDate startDate,
        LocalDate endDate,
        boolean active,
        UserRoleResponse role,
        Instant createdAt,
        Instant updatedAt
) {
    public static UserResponse from(UserAccount user) {
        return new UserResponse(
                user.getId(),
                user.getEmployeeId(),
                user.getFullName(),
                user.getPhoneE164(),
                user.getProfilePhotoKey(),
                user.getBirthDate(),
                user.getStartDate(),
                user.getEndDate(),
                user.isActive(),
                UserRoleResponse.from(user.getRole()),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
