package com.eastapp.backend.knowledge.service;

import com.eastapp.backend.auth.security.AuthenticatedUser;
import com.eastapp.backend.common.api.PageResponse;
import com.eastapp.backend.common.error.ApiException;
import com.eastapp.backend.knowledge.KnowledgeSop;
import com.eastapp.backend.knowledge.KnowledgeSopRepository;
import com.eastapp.backend.knowledge.api.CreateKnowledgeSopRequest;
import com.eastapp.backend.knowledge.api.KnowledgeSopResponse;
import com.eastapp.backend.organisation.Tenant;
import com.eastapp.backend.organisation.TenantRepository;
import com.eastapp.backend.people.UserAccount;
import com.eastapp.backend.people.UserAccountRepository;
import com.eastapp.backend.stock.StockTag;
import com.eastapp.backend.stock.StockTagRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        StockTag tag = tagRepository.findByIdAndTenant_Id(request.tagId(), principal.tenantId())
                .orElseThrow(() -> notFound("STOCK_TAG_NOT_FOUND", "The selected Stock tag was not found."));

        KnowledgeSop saved = sopRepository.save(new KnowledgeSop(
                tenant,
                tag,
                youtubeUrl,
                request.title(),
                request.expectedOutcome(),
                request.description(),
                actor
        ));
        return KnowledgeSopResponse.from(saved, videoId);
    }

    private static ApiException notFound(String code, String message) {
        return new ApiException(HttpStatus.NOT_FOUND, code, message);
    }
}
