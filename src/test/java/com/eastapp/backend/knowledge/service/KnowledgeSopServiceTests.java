package com.eastapp.backend.knowledge.service;

import com.eastapp.backend.auth.security.AuthenticatedUser;
import com.eastapp.backend.common.error.ApiException;
import com.eastapp.backend.knowledge.KnowledgeSop;
import com.eastapp.backend.knowledge.KnowledgeSopLanguage;
import com.eastapp.backend.knowledge.KnowledgeSopRepository;
import com.eastapp.backend.knowledge.api.BulkDeleteKnowledgeSopsRequest;
import com.eastapp.backend.knowledge.api.CreateKnowledgeSopRequest;
import com.eastapp.backend.knowledge.api.KnowledgeSopResponse;
import com.eastapp.backend.knowledge.api.UpdateKnowledgeSopRequest;
import com.eastapp.backend.organisation.Tenant;
import com.eastapp.backend.organisation.TenantRepository;
import com.eastapp.backend.people.SystemRole;
import com.eastapp.backend.people.UserAccount;
import com.eastapp.backend.people.UserAccountRepository;
import com.eastapp.backend.stock.StockTag;
import com.eastapp.backend.stock.StockTagRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeSopServiceTests {
    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID TAG_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID SOP_ID = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final UUID SECOND_SOP_ID = UUID.fromString("00000000-0000-0000-0000-000000000005");
    private static final UUID LINK_GROUP_ID = UUID.fromString("00000000-0000-0000-0000-000000000006");
    private static final String YOUTUBE_URL = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";
    private static final String MYANMAR_YOUTUBE_URL = "https://youtu.be/9bZkp7q19f0";

    @Mock
    private KnowledgeSopRepository sopRepository;
    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private UserAccountRepository userRepository;
    @Mock
    private StockTagRepository tagRepository;
    @Mock
    private KnowledgeSop sop;
    @Mock
    private KnowledgeSop secondSop;
    @Mock
    private StockTag tag;
    @Mock
    private UserAccount actor;
    @Mock
    private Tenant tenant;

    @Test
    void createsASecondLanguageWithTheSelectedGroupsSharedContent() {
        stubTagAndActorResponseDependencies();
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(userRepository.findByIdAndTenant_Id(USER_ID, TENANT_ID)).thenReturn(Optional.of(actor));
        when(sopRepository.findByIdAndTenant_Id(SOP_ID, TENANT_ID)).thenReturn(Optional.of(sop));
        when(sop.getLinkGroupId()).thenReturn(LINK_GROUP_ID);
        when(sop.getTag()).thenReturn(tag);
        when(sop.getTitle()).thenReturn("Belly Pork Preparation");
        when(sop.getExpectedOutcome()).thenReturn("Consistent result");
        when(sop.getDescription()).thenReturn("Shared instructions");
        when(sop.getYoutubeUrl()).thenReturn(YOUTUBE_URL);
        when(sopRepository.findAllByTenant_IdAndLinkGroupIdOrderByCreatedAtAscIdAsc(
                TENANT_ID,
                LINK_GROUP_ID
        )).thenReturn(List.of(sop));
        when(sopRepository.existsByTenant_IdAndLinkGroupIdAndLanguage(
                TENANT_ID,
                LINK_GROUP_ID,
                KnowledgeSopLanguage.MYANMAR
        )).thenReturn(false);
        when(sopRepository.saveAndFlush(any(KnowledgeSop.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        KnowledgeSopResponse response = service().create(
                principal(),
                new CreateKnowledgeSopRequest(
                        MYANMAR_YOUTUBE_URL,
                        TAG_ID,
                        "Ignored different title",
                        "Ignored different outcome",
                        "Ignored different description",
                        KnowledgeSopLanguage.MYANMAR,
                        SOP_ID
                )
        );

        ArgumentCaptor<KnowledgeSop> saved = ArgumentCaptor.forClass(KnowledgeSop.class);
        verify(sopRepository).saveAndFlush(saved.capture());
        assertEquals(LINK_GROUP_ID, saved.getValue().getLinkGroupId());
        assertEquals(KnowledgeSopLanguage.MYANMAR, saved.getValue().getLanguage());
        assertEquals(tag, saved.getValue().getTag());
        assertEquals(MYANMAR_YOUTUBE_URL, saved.getValue().getYoutubeUrl());
        assertEquals("Belly Pork Preparation", saved.getValue().getTitle());
        assertEquals("Consistent result", saved.getValue().getExpectedOutcome());
        assertEquals("Shared instructions", saved.getValue().getDescription());
        assertEquals("9bZkp7q19f0", response.youtubeVideoId());
    }

    @Test
    void rejectsTheSameYouTubeVideoForBothLinkedLanguages() {
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(userRepository.findByIdAndTenant_Id(USER_ID, TENANT_ID)).thenReturn(Optional.of(actor));
        when(sopRepository.findByIdAndTenant_Id(SOP_ID, TENANT_ID)).thenReturn(Optional.of(sop));
        when(sop.getLinkGroupId()).thenReturn(LINK_GROUP_ID);
        when(sop.getYoutubeUrl()).thenReturn(YOUTUBE_URL);
        when(sopRepository.findAllByTenant_IdAndLinkGroupIdOrderByCreatedAtAscIdAsc(
                TENANT_ID,
                LINK_GROUP_ID
        )).thenReturn(List.of(sop));

        ApiException error = assertThrows(
                ApiException.class,
                () -> service().create(
                        principal(),
                        new CreateKnowledgeSopRequest(
                                YOUTUBE_URL,
                                TAG_ID,
                                "Belly Pork Preparation",
                                "Consistent result",
                                "Shared instructions",
                                KnowledgeSopLanguage.MYANMAR,
                                SOP_ID
                        )
                )
        );

        assertEquals("SOP_VIDEO_ALREADY_LINKED", error.getCode());
        verify(sopRepository, never()).saveAndFlush(any(KnowledgeSop.class));
    }

    @Test
    void rejectsAThirdVideoInTheSameLinkedGroup() {
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(userRepository.findByIdAndTenant_Id(USER_ID, TENANT_ID)).thenReturn(Optional.of(actor));
        when(sopRepository.findByIdAndTenant_Id(SOP_ID, TENANT_ID)).thenReturn(Optional.of(sop));
        when(sop.getLinkGroupId()).thenReturn(LINK_GROUP_ID);
        when(sopRepository.findAllByTenant_IdAndLinkGroupIdOrderByCreatedAtAscIdAsc(
                TENANT_ID,
                LINK_GROUP_ID
        )).thenReturn(List.of(sop, secondSop));

        ApiException error = assertThrows(
                ApiException.class,
                () -> service().create(
                        principal(),
                        new CreateKnowledgeSopRequest(
                                YOUTUBE_URL,
                                TAG_ID,
                                "Third video",
                                "Outcome",
                                "Description",
                                KnowledgeSopLanguage.MYANMAR,
                                SOP_ID
                        )
                )
        );

        assertEquals("SOP_LINK_GROUP_FULL", error.getCode());
        verify(sopRepository, never()).saveAndFlush(any(KnowledgeSop.class));
    }

    @Test
    void updatesOneLanguageAndKeepsTheLinkedGroupContentInSync() {
        stubSopResponseDependencies();
        when(sopRepository.findByIdAndTenant_Id(SOP_ID, TENANT_ID)).thenReturn(Optional.of(sop));
        when(tagRepository.findByIdAndTenant_Id(TAG_ID, TENANT_ID)).thenReturn(Optional.of(tag));
        when(sop.getId()).thenReturn(SOP_ID);
        when(sop.getLinkGroupId()).thenReturn(LINK_GROUP_ID);
        when(sop.getLanguage()).thenReturn(KnowledgeSopLanguage.ENGLISH);
        when(secondSop.getId()).thenReturn(SECOND_SOP_ID);
        when(secondSop.getYoutubeUrl()).thenReturn(MYANMAR_YOUTUBE_URL);
        when(sopRepository.findAllByTenant_IdAndLinkGroupIdOrderByCreatedAtAscIdAsc(
                TENANT_ID,
                LINK_GROUP_ID
        )).thenReturn(List.of(sop, secondSop));

        UpdateKnowledgeSopRequest request = new UpdateKnowledgeSopRequest(
                YOUTUBE_URL,
                TAG_ID,
                "Updated SOP",
                "Consistent result",
                "Updated steps",
                KnowledgeSopLanguage.ENGLISH
        );

        KnowledgeSopResponse response = service().update(principal(), SOP_ID, request);

        verify(sopRepository).findByIdAndTenant_Id(SOP_ID, TENANT_ID);
        verify(tagRepository).findByIdAndTenant_Id(TAG_ID, TENANT_ID);
        verify(sop).update(
                tag,
                YOUTUBE_URL,
                "Updated SOP",
                "Consistent result",
                "Updated steps",
                KnowledgeSopLanguage.ENGLISH
        );
        verify(secondSop).updateSharedContent(
                tag,
                "Updated SOP",
                "Consistent result",
                "Updated steps"
        );
        verify(sopRepository).flush();
        assertEquals("dQw4w9WgXcQ", response.youtubeVideoId());
    }

    @Test
    void rejectsUpdatingOneLanguageToTheOtherLanguagesVideo() {
        when(sopRepository.findByIdAndTenant_Id(SOP_ID, TENANT_ID)).thenReturn(Optional.of(sop));
        when(tagRepository.findByIdAndTenant_Id(TAG_ID, TENANT_ID)).thenReturn(Optional.of(tag));
        when(sop.getId()).thenReturn(SOP_ID);
        when(sop.getLinkGroupId()).thenReturn(LINK_GROUP_ID);
        when(secondSop.getId()).thenReturn(SECOND_SOP_ID);
        when(secondSop.getYoutubeUrl()).thenReturn(YOUTUBE_URL);
        when(sopRepository.findAllByTenant_IdAndLinkGroupIdOrderByCreatedAtAscIdAsc(
                TENANT_ID,
                LINK_GROUP_ID
        )).thenReturn(List.of(sop, secondSop));

        ApiException error = assertThrows(
                ApiException.class,
                () -> service().update(
                        principal(),
                        SOP_ID,
                        new UpdateKnowledgeSopRequest(
                                YOUTUBE_URL,
                                TAG_ID,
                                "Belly Pork Preparation",
                                "Consistent result",
                                "Shared instructions",
                                KnowledgeSopLanguage.ENGLISH
                        )
                )
        );

        assertEquals("SOP_VIDEO_ALREADY_LINKED", error.getCode());
        verify(sop, never()).update(
                any(StockTag.class),
                any(String.class),
                any(String.class),
                any(String.class),
                any(String.class),
                any(KnowledgeSopLanguage.class)
        );
    }

    @Test
    void bulkDeleteExpandsASelectedVideoToItsCompleteLinkedGroup() {
        when(sopRepository.findAllByTenant_IdAndIdIn(TENANT_ID, Set.of(SOP_ID)))
                .thenReturn(List.of(sop));
        when(sop.getLinkGroupId()).thenReturn(LINK_GROUP_ID);
        when(sopRepository.findAllByTenant_IdAndLinkGroupIdIn(TENANT_ID, Set.of(LINK_GROUP_ID)))
                .thenReturn(List.of(sop, secondSop));

        service().bulkDelete(
                principal(),
                new BulkDeleteKnowledgeSopsRequest(List.of(SOP_ID))
        );

        verify(sopRepository).deleteAllInBatch(List.of(sop, secondSop));
    }

    @Test
    void bulkDeleteRejectsTheWholeRequestWhenAnySopIsOutsideTheActiveTenant() {
        when(sopRepository.findAllByTenant_IdAndIdIn(TENANT_ID, Set.of(SOP_ID, SECOND_SOP_ID)))
                .thenReturn(List.of(sop));

        ApiException error = assertThrows(
                ApiException.class,
                () -> service().bulkDelete(
                        principal(),
                        new BulkDeleteKnowledgeSopsRequest(List.of(SOP_ID, SECOND_SOP_ID))
                )
        );

        assertEquals("SOP_NOT_FOUND", error.getCode());
        verify(sopRepository, never()).deleteAllInBatch(any());
    }

    private void stubTagAndActorResponseDependencies() {
        when(tag.getId()).thenReturn(TAG_ID);
        when(tag.getTag()).thenReturn("Chiller");
        when(actor.getFullName()).thenReturn("Manager");
    }

    private void stubSopResponseDependencies() {
        stubTagAndActorResponseDependencies();
        when(sop.getTag()).thenReturn(tag);
        when(sop.getCreatedBy()).thenReturn(actor);
        when(sop.getYoutubeUrl()).thenReturn(YOUTUBE_URL);
        when(sop.getTitle()).thenReturn("Updated SOP");
        when(sop.getExpectedOutcome()).thenReturn("Consistent result");
        when(sop.getDescription()).thenReturn("Updated steps");
    }

    private KnowledgeSopService service() {
        return new KnowledgeSopService(
                sopRepository,
                tenantRepository,
                userRepository,
                tagRepository
        );
    }

    private AuthenticatedUser principal() {
        return new AuthenticatedUser(
                UUID.randomUUID(),
                USER_ID,
                TENANT_ID,
                UUID.randomUUID(),
                "E0001",
                "Manager",
                "EAST",
                "The East",
                SystemRole.MANAGER
        );
    }
}
