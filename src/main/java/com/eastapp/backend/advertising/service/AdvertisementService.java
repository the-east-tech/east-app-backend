package com.eastapp.backend.advertising.service;

import com.eastapp.backend.advertising.Advertisement;
import com.eastapp.backend.advertising.AdvertisementRepository;
import com.eastapp.backend.advertising.api.ActiveAdvertisementFeedResponse;
import com.eastapp.backend.advertising.api.AdvertisementResponse;
import com.eastapp.backend.advertising.api.UpsertAdvertisementRequest;
import com.eastapp.backend.auth.security.AuthenticatedUser;
import com.eastapp.backend.common.error.ApiException;
import com.eastapp.backend.organisation.Tenant;
import com.eastapp.backend.organisation.TenantRepository;
import com.eastapp.backend.reports.service.ReportMediaService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class AdvertisementService {
    private static final int MAXIMUM_CONCURRENT_ADVERTISEMENTS = 4;

    private final AdvertisementRepository repository;
    private final TenantRepository tenantRepository;
    private final ReportMediaService mediaService;

    public AdvertisementService(
            AdvertisementRepository repository,
            TenantRepository tenantRepository,
            ReportMediaService mediaService
    ) {
        this.repository = repository;
        this.tenantRepository = tenantRepository;
        this.mediaService = mediaService;
    }

    public ActiveAdvertisementFeedResponse active(AuthenticatedUser principal) {
        Instant now = Instant.now();
        List<Advertisement> enabledAdvertisements = repository
                .findAllByTenant_IdAndActiveTrueAndEndsAtGreaterThan(
                        principal.tenantId(),
                        now
                );
        List<AdvertisementResponse> advertisements = enabledAdvertisements
                .stream()
                .filter(advertisement -> !advertisement.getStartsAt().isAfter(now))
                .sorted(Comparator
                        .comparingInt(Advertisement::getDisplayOrder)
                        .thenComparing(Advertisement::getCreatedAt))
                .map(this::response)
                .toList();
        Instant nextChangeAt = enabledAdvertisements
                .stream()
                .flatMap(advertisement -> {
                    if (advertisement.getStartsAt().isAfter(now)) {
                        return java.util.stream.Stream.of(advertisement.getStartsAt());
                    }
                    return java.util.stream.Stream.of(advertisement.getEndsAt());
                })
                .min(Instant::compareTo)
                .orElse(null);
        return new ActiveAdvertisementFeedResponse(advertisements, now, nextChangeAt);
    }

    public List<AdvertisementResponse> all(AuthenticatedUser principal) {
        requireOwner(principal);
        return repository.findAllByTenant_IdOrderByStartsAtDesc(principal.tenantId())
                .stream()
                .map(this::response)
                .toList();
    }

    @Transactional
    public AdvertisementResponse create(
            AuthenticatedUser principal,
            UpsertAdvertisementRequest request
    ) {
        requireOwner(principal);
        Tenant tenant = lockTenant(principal.tenantId());
        validate(request, null, principal);

        Advertisement advertisement = new Advertisement(
                tenant,
                request.imageStorageKey(),
                request.startsAt(),
                request.endsAt(),
                request.displayOrder(),
                request.active(),
                principal.userId()
        );
        return response(repository.saveAndFlush(advertisement));
    }

    @Transactional
    public AdvertisementResponse update(
            AuthenticatedUser principal,
            UUID advertisementId,
            UpsertAdvertisementRequest request
    ) {
        requireOwner(principal);
        lockTenant(principal.tenantId());
        Advertisement advertisement = requireAdvertisement(principal, advertisementId);
        validate(request, advertisementId, principal);
        advertisement.update(
                request.imageStorageKey(),
                request.startsAt(),
                request.endsAt(),
                request.displayOrder(),
                request.active()
        );
        return response(advertisement);
    }

    @Transactional
    public void delete(AuthenticatedUser principal, UUID advertisementId) {
        requireOwner(principal);
        lockTenant(principal.tenantId());
        repository.delete(requireAdvertisement(principal, advertisementId));
    }

    private void validate(
            UpsertAdvertisementRequest request,
            UUID excludedAdvertisementId,
            AuthenticatedUser principal
    ) {
        if (!request.endsAt().isAfter(request.startsAt())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_ADVERTISEMENT_PERIOD",
                    "End date and time must be later than start date and time."
            );
        }

        mediaService.requireTenantMedia(principal, request.imageStorageKey());
        if (!request.active()) return;

        List<Advertisement> overlappingActiveAdvertisements = repository
                .findAllByTenant_IdAndActiveTrueAndStartsAtLessThanAndEndsAtGreaterThan(
                        principal.tenantId(),
                        request.endsAt(),
                        request.startsAt()
                )
                .stream()
                .filter(advertisement -> excludedAdvertisementId == null
                        || !advertisement.getId().equals(excludedAdvertisementId))
                .toList();

        List<AdvertisementSchedulePolicy.Window> overlappingWindows = overlappingActiveAdvertisements
                .stream()
                .map(advertisement -> new AdvertisementSchedulePolicy.Window(
                        advertisement.getStartsAt(),
                        advertisement.getEndsAt()
                ))
                .toList();

        if (AdvertisementSchedulePolicy.exceedsConcurrentLimit(
                overlappingWindows,
                request.startsAt(),
                request.endsAt(),
                MAXIMUM_CONCURRENT_ADVERTISEMENTS
        )) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "ADVERTISEMENT_ACTIVE_LIMIT",
                    "A maximum of 4 advertisements may be published at the same time."
            );
        }
    }

    private Tenant lockTenant(UUID tenantId) {
        return tenantRepository.findLockedById(tenantId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "TENANT_NOT_FOUND",
                        "Business was not found."
                ));
    }

    private Advertisement requireAdvertisement(
            AuthenticatedUser principal,
            UUID advertisementId
    ) {
        Advertisement advertisement = repository.findById(advertisementId)
                .orElseThrow(() -> notFound());
        if (!advertisement.getTenant().getId().equals(principal.tenantId())) {
            throw notFound();
        }
        return advertisement;
    }

    private ApiException notFound() {
        return new ApiException(
                HttpStatus.NOT_FOUND,
                "ADVERTISEMENT_NOT_FOUND",
                "Advertisement was not found."
        );
    }

    private void requireOwner(AuthenticatedUser principal) {
        if (!principal.isOwner()) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "OWNER_REQUIRED",
                    "Only an Owner can manage advertisements."
            );
        }
    }

    private AdvertisementResponse response(Advertisement advertisement) {
        return new AdvertisementResponse(
                advertisement.getId(),
                advertisement.getImageStorageKey(),
                advertisement.getStartsAt(),
                advertisement.getEndsAt(),
                advertisement.getDisplayOrder(),
                advertisement.isActive(),
                advertisement.getCreatedAt(),
                advertisement.getUpdatedAt()
        );
    }
}
