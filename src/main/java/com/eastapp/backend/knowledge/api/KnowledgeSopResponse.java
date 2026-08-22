package com.eastapp.backend.knowledge.api;

import com.eastapp.backend.knowledge.KnowledgeSop;
import com.eastapp.backend.knowledge.KnowledgeSopLanguage;

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
        KnowledgeSopLanguage language,
        UUID linkGroupId,
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
                sop.getLanguage(),
                sop.getLinkGroupId(),
                sop.getCreatedBy().getFullName(),
                sop.getCreatedAt(),
                sop.getUpdatedAt()
        );
    }
}
