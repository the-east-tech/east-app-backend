package com.eastapp.backend.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface LoginIdentityRepository extends JpaRepository<LoginIdentity, UUID> {
}
