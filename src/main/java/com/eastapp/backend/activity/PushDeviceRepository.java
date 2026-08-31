package com.eastapp.backend.activity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PushDeviceRepository extends JpaRepository<PushDevice, UUID> {
    Optional<PushDevice> findByToken(String token);

    List<PushDevice> findAllByTenantIdAndUserIdInAndActiveTrue(
            UUID tenantId,
            Collection<UUID> userIds
    );
}
