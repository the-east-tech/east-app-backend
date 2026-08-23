package com.eastapp.backend.translation.api;

import com.eastapp.backend.translation.TranslationLanguage;

public record TranslationPreviewResponse(
        TranslationLanguage sourceLanguage,
        TranslationLanguage targetLanguage,
        TranslationLanguage companionTargetLanguage,
        int uniqueTextCount,
        int selectedCacheHits,
        int selectedCacheMisses,
        int companionCacheHits,
        int companionCacheMisses,
        int providerRequestsIfConfirmed,
        boolean providerAvailable
) {
}
