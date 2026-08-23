package com.eastapp.backend.translation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "translation_cache")
public class TranslationCacheEntry {

    @Id
    @Generated
    @ColumnDefault("uuidv7()")
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_language", nullable = false, updatable = false, length = 16)
    private TranslationLanguage sourceLanguage;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_language", nullable = false, updatable = false, length = 16)
    private TranslationLanguage targetLanguage;

    @Column(name = "source_hash", nullable = false, updatable = false, length = 64)
    private String sourceHash;

    @Column(name = "source_text", nullable = false, updatable = false, columnDefinition = "TEXT")
    private String sourceText;

    @Column(name = "translated_text", nullable = false, columnDefinition = "TEXT")
    private String translatedText;

    @Column(nullable = false, updatable = false, length = 40)
    private String provider;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TranslationCacheEntry() {
    }

    public TranslationCacheEntry(
            UUID tenantId,
            TranslationLanguage sourceLanguage,
            TranslationLanguage targetLanguage,
            String sourceHash,
            String sourceText,
            String translatedText,
            String provider
    ) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.sourceLanguage = Objects.requireNonNull(sourceLanguage, "sourceLanguage must not be null");
        this.targetLanguage = Objects.requireNonNull(targetLanguage, "targetLanguage must not be null");
        this.sourceHash = requireText(sourceHash, "sourceHash");
        this.sourceText = requireText(sourceText, "sourceText");
        this.translatedText = requireText(translatedText, "translatedText");
        this.provider = requireText(provider, "provider");
    }

    public String getSourceHash() {
        return sourceHash;
    }

    public String getSourceText() {
        return sourceText;
    }

    public String getTranslatedText() {
        return translatedText;
    }

    private static String requireText(String value, String fieldName) {
        String normalised = Objects.requireNonNull(value, fieldName + " must not be null").trim();
        if (normalised.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return normalised;
    }
}
