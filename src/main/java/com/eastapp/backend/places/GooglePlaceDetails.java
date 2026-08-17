package com.eastapp.backend.places;

public record GooglePlaceDetails(
        String placeId,
        String displayName,
        String formattedAddress,
        double latitude,
        double longitude,
        String googleMapsUri,
        Double rating,
        Integer userRatingCount
) {
}
