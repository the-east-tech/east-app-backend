package com.eastapp.backend.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LoginIdentityRepository extends JpaRepository<LoginIdentity, UUID> {

    Optional<LoginIdentity> findByPhoneE164(String phoneE164);
}
