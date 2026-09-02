package com.eastapp.backend.knowledge.service;

import com.eastapp.backend.auth.security.AuthenticatedUser;
import com.eastapp.backend.common.api.PageResponse;
import com.eastapp.backend.common.error.ApiException;
import com.eastapp.backend.knowledge.KnowledgeSop;
import com.eastapp.backend.knowledge.KnowledgeSopRepository;
import com.eastapp.backend.knowledge.api.BulkDeleteKnowledgeSopsRequest;
import com.eastapp.backend.knowledge.api.CreateKnowledgeSopRequest;
import com.eastapp.backend.knowledge.api.KnowledgeSopResponse;
import com.eastapp.backend.knowledge.api.UpdateKnowledgeSopRequest;
import com.eastapp.backend.organisation.Tenant;
import com.eastapp.backend.organisation.TenantRepository;
import com.eastapp.backend.people.UserAccount;
import com.eastapp.backend.people.UserAccountRepository;
import com.eastapp.backend.stock.StockTag;
import com.eastapp.backend.stock.StockTagRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class KnowledgeSopService {
    private final KnowledgeSopRepository sopRepository;
    private final TenantRepository tenantRepository;
    private final UserAccountRepository userRepository;
    private final StockTagRepository tagRepository;

    public KnowledgeSopService(
            KnowledgeSopRepository sopRepository,
            TenantRepository tenantRepository,
            UserAccountRepository userRepository,
            StockTagRepository tagRepository
    ) {
        this.sopRepository = sopRepository;
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.tagRepository = tagRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<KnowledgeSopResponse> list(
            AuthenticatedUser principal,
            String search,
            UUID tagId,
            int page,
            int size
    ) {
        UUID resolvedTagId = tagId == null ? new UUID(0L, 0L) : tagId;
        return PageResponse.from(
                sopRepository.searchByTenant(
                        principal.tenantId(),
                        tagId != null,
                        resolvedTagId,
                        search == null ? "" : search.trim(),
                        PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100))
                ),
                sop -> KnowledgeSopResponse.from(
                        sop,
                        YouTubeUrlParser.parseVideoId(sop.getYoutubeUrl())
                )
        );
    }

    @Transactional(readOnly = true)
    public KnowledgeSopResponse get(AuthenticatedUser principal, UUID sopId) {
        KnowledgeSop sop = sopRepository.findByIdAndTenant_Id(sopId, principal.tenantId())
                .orElseThrow(() -> notFound("SOP_NOT_FOUND", "SOP not found."));
        return KnowledgeSopResponse.from(sop, YouTubeUrlParser.parseVideoId(sop.getYoutubeUrl()));
    }

    @Transactional(readOnly = true)
    public List<KnowledgeSopResponse> versions(AuthenticatedUser principal, UUID sopId) {
        KnowledgeSop selected = sopRepository.findByIdAndTenant_Id(sopId, principal.tenantId())
                .orElseThrow(() -> notFound("SOP_NOT_FOUND", "SOP not found."));
        return sopRepository.findAllByTenant_IdAndLinkGroupIdOrderByCreatedAtAscIdAsc(
                        principal.tenantId(), selected.getLinkGroupId()
                )
                .stream()
                .map(sop -> KnowledgeSopResponse.from(
                        sop,
                        YouTubeUrlParser.parseVideoId(sop.getYoutubeUrl())
                ))
                .toList();
    }

    @Transactional
    public KnowledgeSopResponse create(
            AuthenticatedUser principal,
            CreateKnowledgeSopRequest request
    ) {
        String youtubeUrl = request.youtubeUrl().trim();
        String videoId = YouTubeUrlParser.parseVideoId(youtubeUrl);
        Tenant tenant = tenantRepository.findById(principal.tenantId())
                .orElseThrow(() -> notFound("TENANT_NOT_FOUND", "Business not found."));
        UserAccount actor = userRepository.findByIdAndTenant_Id(principal.userId(), principal.tenantId())
                .orElseThrow(() -> notFound("USER_NOT_FOUND", "Current user not found."));

        StockTag tag;
        String title = request.title();
        String expectedOutcome = request.expectedOutcome();
        String description = request.description();
        UUID linkGroupId = UUID.randomUUID();
        if (request.linkedSopId() != null) {
            KnowledgeSop linkedSop = sopRepository
                    .findByIdAndTenant_Id(request.linkedSopId(), principal.tenantId())
                    .orElseThrow(() -> notFound(
                            "LINKED_SOP_NOT_FOUND",
                            "The selected linked SOP video was not found in the active business."
                    ));
            List<KnowledgeSop> linkedGroup = sopRepository
                    .findAllByTenant_IdAndLinkGroupIdOrderByCreatedAtAscIdAsc(
                            principal.tenantId(),
                            linkedSop.getLinkGroupId()
                    );
            if (linkedGroup.size() >= 2) {
                throw conflict(
                        "SOP_LINK_GROUP_FULL",
                        "The selected SOP already has the maximum of two linked video versions."
                );
            }
            if (linkedGroup.stream()
                    .map(linked -> YouTubeUrlParser.parseVideoId(linked.getYoutubeUrl()))
                    .anyMatch(videoId::equals)) {
                throw conflict(
                        "SOP_VIDEO_ALREADY_LINKED",
                        "English and Myanmar must use different YouTube videos."
                );
            }
            if (sopRepository.existsByTenant_IdAndLinkGroupIdAndLanguage(
                    principal.tenantId(),
                    linkedSop.getLinkGroupId(),
                    request.language()
            )) {
                throw conflict(
                        "SOP_LANGUAGE_ALREADY_LINKED",
                        "The selected SOP already has this language version."
                );
            }
            tag = linkedSop.getTag();
            title = linkedSop.getTitle();
            expectedOutcome = linkedSop.getExpectedOutcome();
            description = linkedSop.getDescription();
            linkGroupId = linkedSop.getLinkGroupId();
        } else {
            tag = tagRepository.findByIdAndTenant_Id(request.tagId(), principal.tenantId())
                    .orElseThrow(() -> notFound(
                            "STOCK_TAG_NOT_FOUND",
                            "The selected Stock tag was not found."
                    ));
        }

        KnowledgeSop saved;
        try {
            saved = sopRepository.saveAndFlush(new KnowledgeSop(
                    tenant,
                    tag,
                    youtubeUrl,
                    title,
                    expectedOutcome,
                    description,
                    request.language(),
                    linkGroupId,
                    actor
            ));
        } catch (DataIntegrityViolationException exception) {
            throw conflict(
                    "SOP_LINK_CONFLICT",
                    "The linked SOP changed while this video was being created. Reload and try again."
            );
        }
        return KnowledgeSopResponse.from(saved, videoId);
    }

    @Transactional
    public KnowledgeSopResponse update(
            AuthenticatedUser principal,
            UUID sopId,
            UpdateKnowledgeSopRequest request
    ) {
        KnowledgeSop sop = sopRepository.findByIdAndTenant_Id(sopId, principal.tenantId())
                .orElseThrow(() -> notFound("SOP_NOT_FOUND", "SOP not found."));
        StockTag tag = tagRepository.findByIdAndTenant_Id(request.tagId(), principal.tenantId())
                .orElseThrow(() -> notFound("STOCK_TAG_NOT_FOUND", "The selected Stock tag was not found."));
        String youtubeUrl = request.youtubeUrl().trim();
        String videoId = YouTubeUrlParser.parseVideoId(youtubeUrl);

        if (sopRepository.existsByTenant_IdAndLinkGroupIdAndLanguageAndIdNot(
                principal.tenantId(),
                sop.getLinkGroupId(),
                request.language(),
                sop.getId()
        )) {
            throw conflict(
                    "SOP_LANGUAGE_ALREADY_LINKED",
                    "The linked SOP already has this language version."
            );
        }

        List<KnowledgeSop> linkedGroup = sopRepository
                .findAllByTenant_IdAndLinkGroupIdOrderByCreatedAtAscIdAsc(
                        principal.tenantId(),
                        sop.getLinkGroupId()
                );
        if (linkedGroup.stream()
                .filter(linked -> !linked.getId().equals(sop.getId()))
                .map(linked -> YouTubeUrlParser.parseVideoId(linked.getYoutubeUrl()))
                .anyMatch(videoId::equals)) {
            throw conflict(
                    "SOP_VIDEO_ALREADY_LINKED",
                    "English and Myanmar must use different YouTube videos."
            );
        }
        linkedGroup.stream()
                .filter(linked -> !linked.getId().equals(sop.getId()))
                .forEach(linked -> linked.updateSharedContent(
                        tag,
                        request.title(),
                        request.expectedOutcome(),
                        request.description()
                ));

        sop.update(
                tag,
                youtubeUrl,
                request.title(),
                request.expectedOutcome(),
                request.description(),
                request.language()
        );
        try {
            sopRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw conflict(
                    "SOP_LINK_CONFLICT",
                    "The linked SOP changed while this video was being updated. Reload and try again."
            );
        }
        return KnowledgeSopResponse.from(sop, videoId);
    }

    @Transactional
    public void bulkDelete(
            AuthenticatedUser principal,
            BulkDeleteKnowledgeSopsRequest request
    ) {
        Set<UUID> requestedIds = new LinkedHashSet<>(request.sopIds());
        List<KnowledgeSop> sops = sopRepository.findAllByTenant_IdAndIdIn(
                principal.tenantId(),
                requestedIds
        );
        if (sops.size() != requestedIds.size()) {
            throw notFound(
                    "SOP_NOT_FOUND",
                    "One or more selected SOPs were not found in the active business."
            );
        }
        Set<UUID> linkGroupIds = sops.stream()
                .map(KnowledgeSop::getLinkGroupId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        List<KnowledgeSop> completeLinkedGroups = sopRepository
                .findAllByTenant_IdAndLinkGroupIdIn(principal.tenantId(), linkGroupIds);
        sopRepository.deleteAllInBatch(completeLinkedGroups);
    }

    private static ApiException notFound(String code, String message) {
        return new ApiException(HttpStatus.NOT_FOUND, code, message);
    }

    private static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }
}
