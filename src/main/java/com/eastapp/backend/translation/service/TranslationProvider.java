package com.eastapp.backend.translation.service;

import com.eastapp.backend.translation.TranslationLanguage;

public interface TranslationProvider {
    String providerName();

    boolean isConfigured();

    String translate(
            String sourceText,
            TranslationLanguage sourceLanguage,
            TranslationLanguage targetLanguage
    );
}
