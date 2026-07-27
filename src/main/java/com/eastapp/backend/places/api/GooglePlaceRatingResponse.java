package com.eastapp.backend.places.api;

import com.eastapp.backend.organisation.Tenant;
import com.eastapp.backend.places.GooglePlaceDetails;

public record GooglePlaceRatingResponse(
        String businessName,
        String placeId,
        String placeName,
        String formattedAddress,
        String googleMapsUri,
        Double rating,
        Integer userRatingCount,
        String attribution
) {
    public static GooglePlaceRatingResponse from(Tenant tenant, GooglePlaceDetails details) {
        return new GooglePlaceRatingResponse(
                tenant.getBusinessName(),
                details.placeId(),
                details.displayName(),
                details.formattedAddress(),
                details.googleMapsUri(),
                details.rating(),
                details.userRatingCount(),
                "Google Maps"
        );
    }
}
