package com.eastapp.backend.translation.service;

import com.eastapp.backend.auth.security.AuthenticatedUser;
import com.eastapp.backend.common.error.ApiException;
import com.eastapp.backend.people.SystemRole;
import com.eastapp.backend.translation.TranslationCacheEntry;
import com.eastapp.backend.translation.TranslationCacheRepository;
import com.eastapp.backend.translation.TranslationLanguage;
import com.eastapp.backend.translation.api.TranslateTextRequest;
import com.eastapp.backend.translation.api.TranslateTextResponse;
import com.eastapp.backend.translation.api.TranslationPreviewResponse;
import com.eastapp.backend.translation.config.TranslationProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TranslationServiceTests {

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String SOURCE = "Keep the chiller clean";

    @Mock
    private TranslationCacheRepository cacheRepository;
    @Mock
    private TranslationProvider provider;

    @Test
    void reusesStoredTranslationWhileProviderIsDisabled() {
        String hash = TranslationService.hash(SOURCE);
        TranslationCacheEntry myanmar = entry(
                TranslationLanguage.MYANMAR,
                hash,
                "အအေးခန်းကို သန့်ရှင်းထားပါ"
        );
        when(provider.isConfigured()).thenReturn(false);
        when(cacheRepository.findAllByTenantIdAndSourceLanguageAndTargetLanguageAndSourceHashIn(
                eq(TENANT_ID),
                eq(TranslationLanguage.ENGLISH),
                eq(TranslationLanguage.MYANMAR),
                any()
        )).thenReturn(List.of(myanmar));

        TranslateTextResponse response = service().translate(
                principal(),
                new TranslateTextRequest(
                        TranslationLanguage.ENGLISH,
                        TranslationLanguage.MYANMAR,
                        List.of(SOURCE)
                )
        );

        assertEquals("အအေးခန်းကို သန့်ရှင်းထားပါ", response.translations().get(0).translatedText());
        assertTrue(response.translations().get(0).cacheHit());
        verify(provider, never()).translate(anyString(), any(), any());
        verify(cacheRepository, never()).saveAllAndFlush(any());
    }

    @Test
    void translatesAndStoresTheTwoMissingLanguagesFromMyanmarOriginal() {
        when(provider.isConfigured()).thenReturn(true);
        when(provider.providerName()).thenReturn("TEST_PROVIDER");
        when(cacheRepository.findAllByTenantIdAndSourceLanguageAndTargetLanguageAndSourceHashIn(
                any(),
                any(),
                any(),
                any()
        )).thenReturn(List.of());
        when(provider.translate(anyString(), any(), any())).thenAnswer(invocation -> {
            TranslationLanguage target = invocation.getArgument(2);
            return target == TranslationLanguage.ENGLISH
                    ? "Keep the chiller clean"
                    : "保持冷藏柜清洁";
        });

        TranslateTextResponse response = service().translate(
                principal(),
                new TranslateTextRequest(
                        TranslationLanguage.MYANMAR,
                        TranslationLanguage.ENGLISH,
                        List.of("အအေးခန်းကို သန့်ရှင်းထားပါ")
                )
        );

        assertEquals("Keep the chiller clean", response.translations().get(0).translatedText());
        verify(provider).translate(
                "အအေးခန်းကို သန့်ရှင်းထားပါ",
                TranslationLanguage.MYANMAR,
                TranslationLanguage.ENGLISH
        );
        verify(provider).translate(
                "အအေးခန်းကို သန့်ရှင်းထားပါ",
                TranslationLanguage.MYANMAR,
                TranslationLanguage.CHINESE
        );
        verify(cacheRepository, org.mockito.Mockito.times(2)).saveAllAndFlush(any());
    }

    @Test
    void rejectsOnlyCacheMissesWhileProviderIsDisabled() {
        when(provider.isConfigured()).thenReturn(false);
        when(cacheRepository.findAllByTenantIdAndSourceLanguageAndTargetLanguageAndSourceHashIn(
                any(),
                any(),
                any(),
                any()
        )).thenReturn(List.of());

        ApiException exception = assertThrows(ApiException.class, () -> service().translate(
                principal(),
                new TranslateTextRequest(
                        TranslationLanguage.ENGLISH,
                        TranslationLanguage.MYANMAR,
                        List.of(SOURCE)
                )
        ));

        assertEquals("TRANSLATION_PROVIDER_DISABLED", exception.getCode());
        verify(provider, never()).translate(anyString(), any(), any());
    }

    @Test
    void previewsCacheAndProviderUsageWithoutCallingTheProvider() {
        String hash = TranslationService.hash(SOURCE);
        TranslationCacheEntry myanmar = entry(
                TranslationLanguage.MYANMAR,
                hash,
                "အအေးခန်းကို သန့်ရှင်းထားပါ"
        );
        when(provider.isConfigured()).thenReturn(true);
        when(cacheRepository.findAllByTenantIdAndSourceLanguageAndTargetLanguageAndSourceHashIn(
                eq(TENANT_ID),
                eq(TranslationLanguage.ENGLISH),
                eq(TranslationLanguage.MYANMAR),
                any()
        )).thenReturn(List.of(myanmar));
        when(cacheRepository.findAllByTenantIdAndSourceLanguageAndTargetLanguageAndSourceHashIn(
                eq(TENANT_ID),
                eq(TranslationLanguage.ENGLISH),
                eq(TranslationLanguage.CHINESE),
                any()
        )).thenReturn(List.of());

        TranslationPreviewResponse response = service().preview(
                principal(),
                new TranslateTextRequest(
                        TranslationLanguage.ENGLISH,
                        TranslationLanguage.MYANMAR,
                        List.of(SOURCE, "  " + SOURCE + "  ")
                )
        );

        assertEquals(1, response.uniqueTextCount());
        assertEquals(1, response.selectedCacheHits());
        assertEquals(0, response.selectedCacheMisses());
        assertEquals(0, response.companionCacheHits());
        assertEquals(1, response.companionCacheMisses());
        assertEquals(1, response.providerRequestsIfConfirmed());
        assertTrue(response.providerAvailable());
        verify(provider, never()).translate(anyString(), any(), any());
        verify(cacheRepository, never()).saveAllAndFlush(any());
    }

    @Test
    void previewReportsNoProviderCallsWhenProviderIsDisabled() {
        when(provider.isConfigured()).thenReturn(false);
        when(cacheRepository.findAllByTenantIdAndSourceLanguageAndTargetLanguageAndSourceHashIn(
                any(),
                any(),
                any(),
                any()
        )).thenReturn(List.of());

        TranslationPreviewResponse response = service().preview(
                principal(),
                new TranslateTextRequest(
                        TranslationLanguage.ENGLISH,
                        TranslationLanguage.MYANMAR,
                        List.of(SOURCE)
                )
        );

        assertEquals(1, response.selectedCacheMisses());
        assertEquals(1, response.companionCacheMisses());
        assertEquals(0, response.providerRequestsIfConfirmed());
        assertTrue(!response.providerAvailable());
        verify(provider, never()).translate(anyString(), any(), any());
    }

    @Test
    void advertisesAllSixTranslationDirections() {
        when(provider.providerName()).thenReturn("TEST_PROVIDER");

        assertEquals(6, service().status().supportedDirections().size());
        assertTrue(service().status().supportedDirections().contains("MYANMAR:CHINESE"));
        assertTrue(service().status().supportedDirections().contains("MYANMAR:ENGLISH"));
    }

    private TranslationCacheEntry entry(
            TranslationLanguage target,
            String hash,
            String translatedText
    ) {
        return new TranslationCacheEntry(
                TENANT_ID,
                TranslationLanguage.ENGLISH,
                target,
                hash,
                SOURCE,
                translatedText,
                "TEST_PROVIDER"
        );
    }

    private TranslationService service() {
        TranslationProperties properties = new TranslationProperties();
        properties.setMaximumParallelRequests(2);
        return new TranslationService(cacheRepository, provider, properties);
    }

    private AuthenticatedUser principal() {
        return new AuthenticatedUser(
                UUID.randomUUID(),
                UUID.randomUUID(),
                TENANT_ID,
                UUID.randomUUID(),
                "E0001",
                "Owner",
                "EAST",
                "The East",
                SystemRole.OWNER
        );
    }
}
