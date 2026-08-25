package com.eastapp.backend.stock.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CreateStockTagRequest(
        @NotBlank @Size(max = 80) String tag,
        @Size(max = 500) List<@NotNull UUID> assignedUserIds
) {
    public CreateStockTagRequest {
        assignedUserIds = assignedUserIds == null ? List.of() : List.copyOf(assignedUserIds);
    }
}
