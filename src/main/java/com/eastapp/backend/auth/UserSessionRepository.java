package com.eastapp.backend.auth;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {

    @EntityGraph(attributePaths = {
            "identity",
            "userAccount",
            "userAccount.identity",
            "userAccount.tenant",
            "userAccount.role"
    })
    Optional<UserSession> findByTokenHashAndRevokedAtIsNull(byte[] tokenHash);

    @EntityGraph(attributePaths = {
            "identity",
            "userAccount",
            "userAccount.identity",
            "userAccount.tenant",
            "userAccount.role"
    })
    Optional<UserSession> findByIdAndRevokedAtIsNull(UUID id);

    List<UserSession> findAllByUserAccount_IdAndRevokedAtIsNull(UUID userAccountId);

    List<UserSession> findAllByIdentity_IdAndRevokedAtIsNull(UUID identityId);
}
