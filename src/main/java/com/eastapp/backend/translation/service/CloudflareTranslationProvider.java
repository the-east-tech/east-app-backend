package com.eastapp.backend.translation.service;

import com.eastapp.backend.common.error.ApiException;
import com.eastapp.backend.translation.TranslationLanguage;
import com.eastapp.backend.translation.config.TranslationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

@Component
public class CloudflareTranslationProvider implements TranslationProvider {

    private static final String API_BASE = "https://api.cloudflare.com/client/v4/accounts/";

    private final TranslationProperties properties;
    private final JsonMapper jsonMapper;
    private final HttpClient httpClient;

    public CloudflareTranslationProvider(
            TranslationProperties properties,
            JsonMapper jsonMapper
    ) {
        this.properties = properties;
        this.jsonMapper = jsonMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public String providerName() {
        return "CLOUDFLARE_WORKERS_AI";
    }

    @Override
    public boolean isConfigured() {
        return properties.isConfigured();
    }

    @Override
    public String translate(
            String sourceText,
            TranslationLanguage sourceLanguage,
            TranslationLanguage targetLanguage
    ) {
        if (!isConfigured()) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "TRANSLATION_PROVIDER_DISABLED",
                    "The translation provider is disabled or not configured."
            );
        }

        String requestBody;
        try {
            requestBody = jsonMapper.writeValueAsString(Map.of(
                    "text", sourceText,
                    "source_lang", sourceLanguage.providerCode(),
                    "target_lang", targetLanguage.providerCode()
            ));
        } catch (JacksonException exception) {
            throw new IllegalStateException("Could not create translation request", exception);
        }

        URI uri = URI.create(API_BASE
                + URLEncoder.encode(properties.getCloudflareAccountId(), StandardCharsets.UTF_8)
                + "/ai/run/"
                + properties.getModel());
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + properties.getCloudflareApiToken())
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw unavailable("Translation request was interrupted.", exception);
        } catch (IOException exception) {
            throw unavailable("Translation provider could not be reached.", exception);
        }

        if (response.statusCode() == 429) {
            throw new ApiException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "TRANSLATION_FREE_LIMIT_REACHED",
                    "The free translation limit has been reached. Try again after the daily limit resets."
            );
        }
        JsonNode root = parseResponse(response.body());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw unavailable(providerError(root), null);
        }

        JsonNode result = root.path("result");
        String translated = firstText(
                result.path("translated_text"),
                result.path("translation"),
                result.path("text"),
                root.path("translated_text")
        );
        if (translated == null) {
            throw unavailable("Translation provider returned no translated text.", null);
        }
        return translated;
    }

    private JsonNode parseResponse(String body) {
        try {
            return jsonMapper.readTree(body == null ? "" : body);
        } catch (JacksonException exception) {
            throw unavailable("Translation provider returned an invalid response.", exception);
        }
    }

    private static String firstText(JsonNode... nodes) {
        for (JsonNode node : nodes) {
            if (node != null && node.isString()) {
                String value = node.asString("").trim();
                if (!value.isEmpty()) return value;
            }
        }
        return null;
    }

    private static String providerError(JsonNode root) {
        JsonNode errors = root.path("errors");
        if (errors.isArray() && !errors.isEmpty()) {
            String message = errors.get(0).path("message").asString("").trim();
            if (!message.isEmpty()) return "Translation provider error: " + message;
        }
        return "Translation provider rejected the request.";
    }

    private static ApiException unavailable(String message, Throwable cause) {
        ApiException exception = new ApiException(
                HttpStatus.BAD_GATEWAY,
                "TRANSLATION_PROVIDER_ERROR",
                message
        );
        if (cause != null) exception.initCause(cause);
        return exception;
    }
}
