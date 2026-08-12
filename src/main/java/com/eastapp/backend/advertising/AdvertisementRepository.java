package com.eastapp.backend.advertising;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AdvertisementRepository extends JpaRepository<Advertisement, UUID> {
    List<Advertisement> findAllByTenant_IdOrderByStartsAtDesc(UUID tenantId);

    List<Advertisement> findAllByTenant_IdAndActiveTrueAndEndsAtGreaterThan(
            UUID tenantId,
            Instant now
    );

    List<Advertisement>
    findAllByTenant_IdAndActiveTrueAndStartsAtLessThanAndEndsAtGreaterThan(
            UUID tenantId,
            Instant candidateEndsAt,
            Instant candidateStartsAt
    );
}
