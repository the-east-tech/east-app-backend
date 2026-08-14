package com.eastapp.backend.attendance.api;

import com.eastapp.backend.attendance.AttendanceEventType;
import jakarta.validation.constraints.NotNull;

public record GenerateAttendanceQrCodeRequest(
        @NotNull AttendanceEventType eventType
) {
}
