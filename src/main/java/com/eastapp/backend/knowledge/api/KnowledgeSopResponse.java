package com.eastapp.backend.knowledge.api;

import com.eastapp.backend.knowledge.KnowledgeSop;

import java.time.Instant;
import java.util.UUID;

public record KnowledgeSopResponse(
        UUID id,
        String youtubeUrl,
        String youtubeVideoId,
        String tagId,
        String tagName,
        String title,
        String expectedOutcome,
        String description,
        String createdBy,
        Instant createdAt,
        Instant updatedAt
) {
    public static KnowledgeSopResponse from(KnowledgeSop sop, String youtubeVideoId) {
        return new KnowledgeSopResponse(
                sop.getId(),
                sop.getYoutubeUrl(),
                youtubeVideoId,
                sop.getTag().getId().toString(),
                sop.getTag().getTag(),
                sop.getTitle(),
                sop.getExpectedOutcome(),
                sop.getDescription(),
                sop.getCreatedBy().getFullName(),
                sop.getCreatedAt(),
                sop.getUpdatedAt()
        );
    }
}
