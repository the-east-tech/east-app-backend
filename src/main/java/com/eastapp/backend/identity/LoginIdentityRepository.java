package com.eastapp.backend.identity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface LoginIdentityRepository extends JpaRepository<LoginIdentity, UUID> {
}
