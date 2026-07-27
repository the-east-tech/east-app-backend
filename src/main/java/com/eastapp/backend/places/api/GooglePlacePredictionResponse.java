package com.eastapp.backend.places.api;

public record GooglePlacePredictionResponse(
        String placeId,
        String mainText,
        String secondaryText,
        String fullText
) {
}
