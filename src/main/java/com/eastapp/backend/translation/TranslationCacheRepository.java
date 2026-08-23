package com.eastapp.backend.translation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TranslationCacheRepository extends JpaRepository<TranslationCacheEntry, UUID> {

    List<TranslationCacheEntry> findAllByTenantIdAndSourceLanguageAndTargetLanguageAndSourceHashIn(
            UUID tenantId,
            TranslationLanguage sourceLanguage,
            TranslationLanguage targetLanguage,
            Collection<String> sourceHashes
    );
}
