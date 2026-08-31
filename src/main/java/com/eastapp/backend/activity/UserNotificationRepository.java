package com.eastapp.backend.activity;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserNotificationRepository extends JpaRepository<UserNotification, UUID> {
    @EntityGraph(attributePaths = "activityEvent")
    Page<UserNotification> findAllByTenantIdAndRecipientUserIdAndDismissedAtIsNullOrderByCreatedAtDescIdDesc(
            UUID tenantId,
            UUID recipientUserId,
            Pageable pageable
    );

    @EntityGraph(attributePaths = "activityEvent")
    Optional<UserNotification> findByIdAndTenantIdAndRecipientUserIdAndDismissedAtIsNull(
            UUID id,
            UUID tenantId,
            UUID recipientUserId
    );

    long countByTenantIdAndRecipientUserIdAndReadAtIsNullAndDismissedAtIsNull(
            UUID tenantId,
            UUID recipientUserId
    );
}
