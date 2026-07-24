package com.eastapp.backend.identity;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {

    @EntityGraph(attributePaths = {
            "userAccount",
            "userAccount.tenant",
            "userAccount.role"
    })
    Optional<UserSession> findByTokenHashAndRevokedAtIsNull(byte[] tokenHash);

    List<UserSession> findAllByUserAccount_IdAndRevokedAtIsNull(UUID userAccountId);
}
