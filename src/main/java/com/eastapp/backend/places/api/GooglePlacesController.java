package com.eastapp.backend.places.api;

import com.eastapp.backend.auth.LoginIdentityRepository;
import com.eastapp.backend.auth.security.AuthenticatedUser;
import com.eastapp.backend.common.error.ApiException;
import com.eastapp.backend.organisation.Tenant;
import com.eastapp.backend.organisation.TenantRepository;
import com.eastapp.backend.places.GooglePlaceDetails;
import com.eastapp.backend.places.service.GooglePlacesService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class GooglePlacesController {

    private final GooglePlacesService googlePlacesService;
    private final LoginIdentityRepository loginIdentityRepository;
    private final TenantRepository tenantRepository;

    public GooglePlacesController(
            GooglePlacesService googlePlacesService,
            LoginIdentityRepository loginIdentityRepository,
            TenantRepository tenantRepository
    ) {
        this.googlePlacesService = googlePlacesService;
        this.loginIdentityRepository = loginIdentityRepository;
        this.tenantRepository = tenantRepository;
    }

    @GetMapping("/api/v1/setup/google-places/autocomplete")
    List<GooglePlacePredictionResponse> setupAutocomplete(
            @RequestParam String query
    ) {
        assertInitialSetupOpen();
        return googlePlacesService.autocomplete(query);
    }

    @GetMapping("/api/v1/setup/google-places/{placeId}")
    GooglePlaceDetailsResponse setupPlaceDetails(@PathVariable String placeId) {
        assertInitialSetupOpen();
        return GooglePlaceDetailsResponse.from(googlePlacesService.placeDetails(placeId));
    }

    @GetMapping("/api/v1/google-places/autocomplete")
    @PreAuthorize("hasAnyRole('OWNER', 'HEAD')")
    List<GooglePlacePredictionResponse> autocomplete(@RequestParam String query) {
        return googlePlacesService.autocomplete(query);
    }

    @GetMapping("/api/v1/google-places/details/{placeId}")
    @PreAuthorize("hasAnyRole('OWNER', 'HEAD')")
    GooglePlaceDetailsResponse placeDetails(@PathVariable String placeId) {
        return GooglePlaceDetailsResponse.from(googlePlacesService.placeDetails(placeId));
    }

    @GetMapping("/api/v1/google-places/current-rating")
    GooglePlaceRatingResponse currentRating(
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        Tenant tenant = tenantRepository.findById(principal.tenantId())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "TENANT_NOT_FOUND",
                        "Tenant not found."
                ));
        GooglePlaceDetails details = googlePlacesService.placeDetails(tenant.getGooglePlaceId());
        return GooglePlaceRatingResponse.from(tenant, details);
    }

    private void assertInitialSetupOpen() {
        if (loginIdentityRepository.count() != 0) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "SETUP_ALREADY_COMPLETED",
                    "Initial setup has already been completed."
            );
        }
    }
}
