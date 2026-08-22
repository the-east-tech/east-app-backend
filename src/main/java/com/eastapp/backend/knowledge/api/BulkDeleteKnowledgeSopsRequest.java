package com.eastapp.backend.knowledge.api;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record BulkDeleteKnowledgeSopsRequest(
        @NotEmpty @Size(max = 100) List<@NotNull UUID> sopIds
) {}
