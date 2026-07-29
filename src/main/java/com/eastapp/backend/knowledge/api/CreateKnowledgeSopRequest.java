package com.eastapp.backend.knowledge.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateKnowledgeSopRequest(
        @NotBlank @Size(max = 500) String youtubeUrl,
        @NotNull UUID tagId,
        @NotBlank @Size(max = 160) String title,
        @NotBlank @Size(max = 1000) String expectedOutcome,
        @NotBlank @Size(max = 5000) String description
) {}
