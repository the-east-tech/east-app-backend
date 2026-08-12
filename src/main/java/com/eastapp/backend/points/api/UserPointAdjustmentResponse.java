package com.eastapp.backend.points.api;

import com.eastapp.backend.points.UserPointAdjustment;

import java.time.Instant;
import java.util.UUID;

public record UserPointAdjustmentResponse(
        UUID id,
        UUID userId,
        String employeeId,
        String fullName,
        int pointsDelta,
        long totalPoints,
        String reason,
        UUID adjustedByUserId,
        String adjustedByName,
        Instant createdAt
) {
    public static UserPointAdjustmentResponse from(
            UserPointAdjustment adjustment,
            long totalPoints
    ) {
        return new UserPointAdjustmentResponse(
                adjustment.getId(),
                adjustment.getRecipient().getId(),
                adjustment.getRecipient().getEmployeeId(),
                adjustment.getRecipient().getFullName(),
                adjustment.getPointsDelta(),
                totalPoints,
                adjustment.getReason(),
                adjustment.getAdjustedBy().getId(),
                adjustment.getAdjustedBy().getFullName(),
                adjustment.getCreatedAt()
        );
    }
}
