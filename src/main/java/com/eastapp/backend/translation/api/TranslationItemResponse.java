package com.eastapp.backend.translation.api;

public record TranslationItemResponse(
        String sourceText,
        String translatedText,
        boolean cacheHit
) {
}
