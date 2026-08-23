package com.eastapp.backend.translation.api;

import java.util.List;

public record TranslationStatusResponse(
        boolean available,
        String provider,
        String model,
        List<String> supportedDirections
) {
}
