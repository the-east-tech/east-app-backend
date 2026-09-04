package com.eastapp.backend.knowledge.service;

import com.eastapp.backend.auth.security.AuthenticatedUser;
import com.eastapp.backend.common.error.ApiException;
import com.eastapp.backend.knowledge.KnowledgeSop;
import com.eastapp.backend.knowledge.KnowledgeSopRepository;
import com.eastapp.backend.knowledge.KnowledgeSopWatchSession;
import com.eastapp.backend.knowledge.KnowledgeSopWatchSessionRepository;
import com.eastapp.backend.knowledge.api.RecordSopWatchTimeRequest;
import com.eastapp.backend.knowledge.api.SopImpactAuditResponse;
import com.eastapp.backend.knowledge.api.SopPlaybackImpactResponse;
import com.eastapp.backend.knowledge.api.UserSopAuditResponse;
import com.eastapp.backend.knowledge.api.UserSopPlaybackResponse;
import com.eastapp.backend.organisation.Tenant;
import com.eastapp.backend.organisation.TenantRepository;
import com.eastapp.backend.people.UserAccount;
import com.eastapp.backend.people.UserAccountRepository;
import com.eastapp.backend.people.api.UserResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class KnowledgeSopAuditService {
    private final KnowledgeSopWatchSessionRepository watchRepository;
    private final KnowledgeSopRepository sopRepository;
    private final UserAccountRepository userRepository;
    private final TenantRepository tenantRepository;

    public KnowledgeSopAuditService(
            KnowledgeSopWatchSessionRepository watchRepository,
            KnowledgeSopRepository sopRepository,
            UserAccountRepository userRepository,
            TenantRepository tenantRepository
    ) {
        this.watchRepository = watchRepository;
        this.sopRepository = sopRepository;
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
    }

    @Transactional
    public void record(
            AuthenticatedUser principal,
            UUID sopId,
            RecordSopWatchTimeRequest request
    ) {
        Instant capturedAt = Instant.now();
        KnowledgeSopWatchSession existing = watchRepository
                .findLockedById(request.sessionId())
                .orElse(null);
        if (existing != null) {
            requireMatchingSession(existing, principal, sopId);
            existing.recordCumulativePlayedSeconds(request.playedSeconds(), capturedAt);
            return;
        }

        KnowledgeSop sop = sopRepository.findByIdAndTenant_Id(sopId, principal.tenantId())
                .orElseThrow(() -> notFound("SOP_NOT_FOUND", "SOP not found."));
        UserAccount user = userRepository.findByIdAndTenant_Id(
                        principal.userId(),
                        principal.tenantId()
                )
                .orElseThrow(() -> notFound("USER_NOT_FOUND", "Current user not found."));
        Tenant tenant = tenantRepository.findById(principal.tenantId())
                .orElseThrow(() -> notFound("TENANT_NOT_FOUND", "Business not found."));
        watchRepository.save(new KnowledgeSopWatchSession(
                request.sessionId(),
                tenant,
                user,
                sop,
                request.playedSeconds(),
                capturedAt
        ));
    }

    @Transactional(readOnly = true)
    public UserSopAuditResponse forUser(AuthenticatedUser principal, UUID userId) {
        UserAccount user = userRepository
                .findByIdAndTenant_IdAndActiveTrue(userId, principal.tenantId())
                .orElseThrow(() -> notFound("USER_NOT_FOUND", "Employee not found."));
        if (!principal.systemRole().canView(user.getRole().getSystemKey())) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "KNOWLEDGE_AUDIT_USER_DENIED",
                    "This employee is above the current user's visibility level."
            );
        }
        List<UserSopPlaybackResponse> videos = watchRepository
                .aggregateForUser(principal.tenantId(), userId)
                .stream()
                .map(UserSopPlaybackResponse::from)
                .toList();
        long total = videos.stream()
                .mapToLong(UserSopPlaybackResponse::totalPlayedSeconds)
                .sum();
        return new UserSopAuditResponse(UserResponse.from(user), total, videos);
    }

    @Transactional(readOnly = true)
    public SopImpactAuditResponse impact(AuthenticatedUser principal) {
        List<SopPlaybackImpactResponse> videos = watchRepository
                .aggregateImpact(principal.tenantId())
                .stream()
                .map(SopPlaybackImpactResponse::from)
                .toList();
        long total = videos.stream()
                .mapToLong(SopPlaybackImpactResponse::totalPlayedSeconds)
                .sum();
        long viewers = watchRepository.countDistinctViewers(principal.tenantId());
        return new SopImpactAuditResponse(total, viewers, videos);
    }

    private static void requireMatchingSession(
            KnowledgeSopWatchSession session,
            AuthenticatedUser principal,
            UUID sopId
    ) {
        boolean matches = session.getTenant().getId().equals(principal.tenantId())
                && session.getUser().getId().equals(principal.userId())
                && session.getSop().getId().equals(sopId);
        if (!matches) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "SOP_WATCH_SESSION_CONFLICT",
                    "This playback session belongs to a different employee or SOP video."
            );
        }
    }

    private static ApiException notFound(String code, String message) {
        return new ApiException(HttpStatus.NOT_FOUND, code, message);
    }
}
