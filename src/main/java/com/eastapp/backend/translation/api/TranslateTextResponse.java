package com.eastapp.backend.translation.api;

import com.eastapp.backend.translation.TranslationLanguage;

import java.util.List;

public record TranslateTextResponse(
        TranslationLanguage sourceLanguage,
        TranslationLanguage targetLanguage,
        List<TranslationItemResponse> translations
) {
}
