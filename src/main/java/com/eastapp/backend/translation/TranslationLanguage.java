package com.eastapp.backend.translation;

public enum TranslationLanguage {
    ENGLISH("en"),
    CHINESE("zh"),
    MYANMAR("my");

    private final String providerCode;

    TranslationLanguage(String providerCode) {
        this.providerCode = providerCode;
    }

    public String providerCode() {
        return providerCode;
    }
}
