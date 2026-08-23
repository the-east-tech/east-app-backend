package com.eastapp.backend.translation.service;

import com.eastapp.backend.auth.security.AuthenticatedUser;
import com.eastapp.backend.common.error.ApiException;
import com.eastapp.backend.translation.TranslationCacheEntry;
import com.eastapp.backend.translation.TranslationCacheRepository;
import com.eastapp.backend.translation.TranslationLanguage;
import com.eastapp.backend.translation.api.TranslateTextRequest;
import com.eastapp.backend.translation.api.TranslateTextResponse;
import com.eastapp.backend.translation.api.TranslationItemResponse;
import com.eastapp.backend.translation.api.TranslationPreviewResponse;
import com.eastapp.backend.translation.api.TranslationStatusResponse;
import com.eastapp.backend.translation.config.TranslationProperties;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Service
public class TranslationService {

    private static final Set<String> SUPPORTED_DIRECTIONS = Set.of(
            "ENGLISH:MYANMAR",
            "ENGLISH:CHINESE",
            "CHINESE:ENGLISH",
            "CHINESE:MYANMAR",
            "MYANMAR:ENGLISH",
            "MYANMAR:CHINESE"
    );

    private final TranslationCacheRepository cacheRepository;
    private final TranslationProvider translationProvider;
    private final TranslationProperties properties;

    public TranslationService(
            TranslationCacheRepository cacheRepository,
            TranslationProvider translationProvider,
            TranslationProperties properties
    ) {
        this.cacheRepository = cacheRepository;
        this.translationProvider = translationProvider;
        this.properties = properties;
    }

    public TranslationStatusResponse status() {
        return new TranslationStatusResponse(
                translationProvider.isConfigured(),
                translationProvider.providerName(),
                properties.getModel(),
                SUPPORTED_DIRECTIONS.stream().sorted().toList()
        );
    }

    public TranslateTextResponse translate(
            AuthenticatedUser principal,
            TranslateTextRequest request
    ) {
        requireSupported(request.sourceLanguage(), request.targetLanguage());

        LinkedHashMap<String, String> sourceByHash = sourceByHash(request.texts());

        TranslationResolution selectedResolution = resolveTranslations(
                principal.tenantId(),
                request.sourceLanguage(),
                request.targetLanguage(),
                sourceByHash
        );
        if (translationProvider.isConfigured()) {
            TranslationLanguage companionTarget = companionTarget(
                    request.sourceLanguage(),
                    request.targetLanguage()
            );
            resolveTranslations(
                    principal.tenantId(),
                    request.sourceLanguage(),
                    companionTarget,
                    sourceByHash
            );
        }

        List<TranslationItemResponse> responses = new ArrayList<>(sourceByHash.size());
        sourceByHash.forEach((sourceHash, sourceText) -> {
            TranslationCacheEntry cached = selectedResolution.cachedByHash().get(sourceHash);
            String translated = cached == null
                    ? selectedResolution.generatedByHash().get(sourceHash)
                    : cached.getTranslatedText();
            responses.add(new TranslationItemResponse(
                    sourceText,
                    translated,
                    cached != null
            ));
        });
        return new TranslateTextResponse(
                request.sourceLanguage(),
                request.targetLanguage(),
                List.copyOf(responses)
        );
    }

    public TranslationPreviewResponse preview(
            AuthenticatedUser principal,
            TranslateTextRequest request
    ) {
        requireSupported(request.sourceLanguage(), request.targetLanguage());

        LinkedHashMap<String, String> sourceByHash = sourceByHash(request.texts());
        TranslationLanguage companionTarget = companionTarget(
                request.sourceLanguage(),
                request.targetLanguage()
        );
        int selectedCacheHits = cachedByHash(
                principal.tenantId(),
                request.sourceLanguage(),
                request.targetLanguage(),
                sourceByHash
        ).size();
        int companionCacheHits = cachedByHash(
                principal.tenantId(),
                request.sourceLanguage(),
                companionTarget,
                sourceByHash
        ).size();
        int selectedCacheMisses = sourceByHash.size() - selectedCacheHits;
        int companionCacheMisses = sourceByHash.size() - companionCacheHits;
        boolean providerAvailable = translationProvider.isConfigured();

        return new TranslationPreviewResponse(
                request.sourceLanguage(),
                request.targetLanguage(),
                companionTarget,
                sourceByHash.size(),
                selectedCacheHits,
                selectedCacheMisses,
                companionCacheHits,
                companionCacheMisses,
                providerAvailable ? selectedCacheMisses + companionCacheMisses : 0,
                providerAvailable
        );
    }

    private TranslationResolution resolveTranslations(
            UUID tenantId,
            TranslationLanguage sourceLanguage,
            TranslationLanguage targetLanguage,
            LinkedHashMap<String, String> sourceByHash
    ) {
        Map<String, TranslationCacheEntry> cachedByHash = cachedByHash(
                tenantId,
                sourceLanguage,
                targetLanguage,
                sourceByHash
        );
        LinkedHashMap<String, String> missingByHash = new LinkedHashMap<>();
        sourceByHash.forEach((sourceHash, sourceText) -> {
            if (!cachedByHash.containsKey(sourceHash)) {
                missingByHash.put(sourceHash, sourceText);
            }
        });

        Map<String, String> generatedByHash = translateMissing(
                missingByHash,
                sourceLanguage,
                targetLanguage
        );
        if (!generatedByHash.isEmpty()) {
            persistGenerated(
                    tenantId,
                    sourceLanguage,
                    targetLanguage,
                    missingByHash,
                    generatedByHash
            );
        }
        return new TranslationResolution(cachedByHash, generatedByHash);
    }

    private Map<String, TranslationCacheEntry> cachedByHash(
            UUID tenantId,
            TranslationLanguage sourceLanguage,
            TranslationLanguage targetLanguage,
            Map<String, String> sourceByHash
    ) {
        if (sourceByHash.isEmpty()) return Map.of();
        Map<String, TranslationCacheEntry> result = new LinkedHashMap<>();
        List<TranslationCacheEntry> entries = cacheRepository
                .findAllByTenantIdAndSourceLanguageAndTargetLanguageAndSourceHashIn(
                        tenantId,
                        sourceLanguage,
                        targetLanguage,
                        sourceByHash.keySet()
                );
        for (TranslationCacheEntry entry : entries) {
            String expectedSource = sourceByHash.get(entry.getSourceHash());
            if (entry.getSourceText().equals(expectedSource)) {
                result.put(entry.getSourceHash(), entry);
            }
        }
        return result;
    }

    private Map<String, String> translateMissing(
            LinkedHashMap<String, String> missingByHash,
            TranslationLanguage sourceLanguage,
            TranslationLanguage targetLanguage
    ) {
        if (missingByHash.isEmpty()) return Map.of();
        if (!translationProvider.isConfigured()) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "TRANSLATION_PROVIDER_DISABLED",
                    "The translation provider is disabled or not configured, and no stored translation is available."
            );
        }
        int workerCount = Math.min(properties.getMaximumParallelRequests(), missingByHash.size());
        ExecutorService executor = Executors.newFixedThreadPool(workerCount);
        try {
            LinkedHashMap<String, Future<String>> futureByHash = new LinkedHashMap<>();
            missingByHash.forEach((sourceHash, sourceText) ->
                    futureByHash.put(sourceHash, executor.submit(() ->
                            translationProvider.translate(sourceText, sourceLanguage, targetLanguage)
                    ))
            );

            LinkedHashMap<String, String> translatedByHash = new LinkedHashMap<>();
            for (Map.Entry<String, Future<String>> entry : futureByHash.entrySet()) {
                translatedByHash.put(entry.getKey(), await(entry.getValue()));
            }
            return translatedByHash;
        } finally {
            executor.shutdownNow();
        }
    }

    private void persistGenerated(
            UUID tenantId,
            TranslationLanguage sourceLanguage,
            TranslationLanguage targetLanguage,
            Map<String, String> sourceByHash,
            Map<String, String> generatedByHash
    ) {
        List<TranslationCacheEntry> entries = new ArrayList<>(generatedByHash.size());
        generatedByHash.forEach((sourceHash, translatedText) -> entries.add(new TranslationCacheEntry(
                tenantId,
                sourceLanguage,
                targetLanguage,
                sourceHash,
                sourceByHash.get(sourceHash),
                translatedText,
                translationProvider.providerName()
        )));
        try {
            cacheRepository.saveAllAndFlush(entries);
        } catch (DataIntegrityViolationException ignored) {
            // Another concurrent request may have stored the same translation first.
        }
    }

    private static String await(Future<String> future) {
        try {
            return future.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "TRANSLATION_INTERRUPTED",
                    "Translation was interrupted."
            );
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Translation failed", cause);
        }
    }

    private static void requireSupported(
            TranslationLanguage sourceLanguage,
            TranslationLanguage targetLanguage
    ) {
        String direction = sourceLanguage.name() + ":" + targetLanguage.name();
        if (!SUPPORTED_DIRECTIONS.contains(direction)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "UNSUPPORTED_TRANSLATION_DIRECTION",
                    "The selected translation direction is not supported."
            );
        }
    }

    private static TranslationLanguage companionTarget(
            TranslationLanguage sourceLanguage,
            TranslationLanguage selectedTarget
    ) {
        for (TranslationLanguage language : TranslationLanguage.values()) {
            if (language != sourceLanguage && language != selectedTarget) {
                return language;
            }
        }
        throw new IllegalStateException("No companion translation language is available");
    }

    private static LinkedHashMap<String, String> sourceByHash(List<String> values) {
        LinkedHashMap<String, String> sourceByHash = new LinkedHashMap<>();
        for (String value : values) {
            String sourceText = normalise(value);
            sourceByHash.putIfAbsent(hash(sourceText), sourceText);
        }
        return sourceByHash;
    }

    static String normalise(String value) {
        String normalised = value
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .strip();
        return Normalizer.normalize(normalised, Normalizer.Form.NFC);
    }

    static String hash(String sourceText) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(sourceText.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private record TranslationResolution(
            Map<String, TranslationCacheEntry> cachedByHash,
            Map<String, String> generatedByHash
    ) {
    }
}
