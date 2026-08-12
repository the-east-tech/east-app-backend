package com.eastapp.backend.places.api;

import com.eastapp.backend.places.GooglePlaceDetails;

public record GooglePlaceDetailsResponse(
        String placeId,
        String displayName,
        String formattedAddress,
        double latitude,
        double longitude,
        String googleMapsUri,
        Double rating,
        Integer userRatingCount
) {
    public static GooglePlaceDetailsResponse from(GooglePlaceDetails details) {
        return new GooglePlaceDetailsResponse(
                details.placeId(),
                details.displayName(),
                details.formattedAddress(),
                details.latitude(),
                details.longitude(),
                details.googleMapsUri(),
                details.rating(),
                details.userRatingCount()
        );
    }
}
