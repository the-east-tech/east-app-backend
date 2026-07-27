package com.eastapp.backend.places.service;

import com.eastapp.backend.common.error.ApiException;
import com.eastapp.backend.places.GooglePlaceDetails;
import com.eastapp.backend.places.api.GooglePlacePredictionResponse;
import com.eastapp.backend.places.config.GooglePlacesProperties;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GooglePlacesService {

    private static final URI AUTOCOMPLETE_URI =
            URI.create("https://places.googleapis.com/v1/places:autocomplete");
    private static final String PLACE_DETAILS_BASE =
            "https://places.googleapis.com/v1/places/";
    private static final String DETAILS_FIELD_MASK =
            "id,displayName,formattedAddress,location,googleMapsUri,rating,userRatingCount";

    private final GooglePlacesProperties properties;
    private final JsonMapper jsonMapper;
    private final HttpClient httpClient;
    private final Map<String, CachedDetails> detailsCache = new ConcurrentHashMap<>();

    public GooglePlacesService(
            GooglePlacesProperties properties,
            JsonMapper jsonMapper
    ) {
        this.properties = properties;
        this.jsonMapper = jsonMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public List<GooglePlacePredictionResponse> autocomplete(String query) {
        String input = requireText(query, "query");
        if (input.length() < 2) return List.of();

        String body;
        try {
            body = jsonMapper.writeValueAsString(Map.of(
                    "input", input,
                    "includedRegionCodes", List.of(properties.getRegionCode()),
                    "languageCode", properties.getLanguageCode()
            ));
        } catch (JacksonException exception) {
            throw new IllegalStateException("Could not create Google Places request", exception);
        }

        HttpRequest request = requestBuilder(AUTOCOMPLETE_URI)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        JsonNode root = send(request);
        List<GooglePlacePredictionResponse> predictions = new ArrayList<>();
        for (JsonNode suggestion : root.path("suggestions")) {
            JsonNode prediction = suggestion.path("placePrediction");
            String placeId = prediction.path("placeId").asString("").trim();
            if (placeId.isEmpty()) continue;
            String fullText = prediction.path("text").path("text").asString("").trim();
            String mainText = prediction.path("structuredFormat")
                    .path("mainText").path("text").asString(fullText).trim();
            String secondaryText = prediction.path("structuredFormat")
                    .path("secondaryText").path("text").asString("").trim();
            predictions.add(new GooglePlacePredictionResponse(
                    placeId, mainText, secondaryText, fullText
            ));
        }
        return List.copyOf(predictions);
    }

    public GooglePlaceDetails placeDetails(String placeId) {
        String normalisedPlaceId = requireText(placeId, "placeId");
        CachedDetails cached = detailsCache.get(normalisedPlaceId);
        Instant now = Instant.now();
        if (cached != null && cached.expiresAt().isAfter(now)) {
            return cached.details();
        }

        String encoded = URLEncoder.encode(normalisedPlaceId, StandardCharsets.UTF_8);
        URI uri = URI.create(PLACE_DETAILS_BASE + encoded
                + "?languageCode=" + URLEncoder.encode(properties.getLanguageCode(), StandardCharsets.UTF_8)
                + "&regionCode=" + URLEncoder.encode(properties.getRegionCode(), StandardCharsets.UTF_8));
        HttpRequest request = requestBuilder(uri)
                .header("X-Goog-FieldMask", DETAILS_FIELD_MASK)
                .GET()
                .build();
        JsonNode root = send(request);

        String resolvedId = requiredNodeText(root, "id", "Google place ID");
        String displayName = requiredNodeText(root.path("displayName"), "text", "Google place name");
        String formattedAddress = requiredNodeText(root, "formattedAddress", "Google formatted address");
        JsonNode location = root.path("location");
        if (!location.hasNonNull("latitude") || !location.hasNonNull("longitude")) {
            throw upstreamInvalid("Google Places returned no coordinates for this location.");
        }
        double latitude = location.path("latitude").asDouble();
        double longitude = location.path("longitude").asDouble();
        String googleMapsUri = nullableNodeText(root, "googleMapsUri");
        Double rating = root.hasNonNull("rating") ? root.path("rating").asDouble() : null;
        Integer userRatingCount = root.hasNonNull("userRatingCount")
                ? root.path("userRatingCount").asInt()
                : null;

        GooglePlaceDetails details = new GooglePlaceDetails(
                resolvedId,
                displayName,
                formattedAddress,
                latitude,
                longitude,
                googleMapsUri,
                rating,
                userRatingCount
        );
        detailsCache.put(normalisedPlaceId, new CachedDetails(
                details,
                now.plus(Duration.ofMinutes(properties.getRatingCacheMinutes()))
        ));
        return details;
    }

    private HttpRequest.Builder requestBuilder(URI uri) {
        String apiKey = properties.getApiKey();
        if (apiKey == null || apiKey.isBlank() || apiKey.startsWith("PASTE_")) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "GOOGLE_PLACES_NOT_CONFIGURED",
                    "Replace HARDCODED_API_KEY in GooglePlacesProperties.java with the Google Maps server key."
            );
        }
        return HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json")
                .header("X-Goog-Api-Key", apiKey);
    }

    private JsonNode send(HttpRequest request) {
        HttpResponse<String> response;
        try {
            response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "GOOGLE_PLACES_INTERRUPTED",
                    "Google Places request was interrupted."
            );
        } catch (IOException exception) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "GOOGLE_PLACES_UNAVAILABLE",
                    "Google Places could not be reached: " + exception.getMessage()
            );
        }

        JsonNode body;
        try {
            body = response.body() == null || response.body().isBlank()
                    ? jsonMapper.createObjectNode()
                    : jsonMapper.readTree(response.body());
        } catch (JacksonException exception) {
            throw upstreamInvalid("Google Places returned invalid JSON.");
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String upstreamMessage = body.path("error").path("message")
                    .asString("Google Places request failed.");
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "GOOGLE_PLACES_ERROR",
                    upstreamMessage
            );
        }
        return body;
    }

    private static String requiredNodeText(JsonNode node, String field, String label) {
        String value = nullableNodeText(node, field);
        if (value == null) {
            throw upstreamInvalid(label + " is missing.");
        }
        return value;
    }

    private static String nullableNodeText(JsonNode node, String field) {
        if (!node.hasNonNull(field)) return null;
        String value = node.path(field).asString("").trim();
        return value.isEmpty() ? null : value;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    private static ApiException upstreamInvalid(String message) {
        return new ApiException(
                HttpStatus.BAD_GATEWAY,
                "GOOGLE_PLACES_INVALID_RESPONSE",
                message
        );
    }

    private record CachedDetails(GooglePlaceDetails details, Instant expiresAt) {
    }
}
