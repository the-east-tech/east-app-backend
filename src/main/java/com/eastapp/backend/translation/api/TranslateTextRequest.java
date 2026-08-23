package com.eastapp.backend.translation.api;

import com.eastapp.backend.translation.TranslationLanguage;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record TranslateTextRequest(
        @NotNull TranslationLanguage sourceLanguage,
        @NotNull TranslationLanguage targetLanguage,
        @NotNull @Size(min = 1, max = 100)
        List<@NotBlank @Size(max = 2000) String> texts
) {
}
