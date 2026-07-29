package com.eastapp.backend.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "login_identities")
public class LoginIdentity {

    @Id
    @Generated
    @ColumnDefault("uuidv7()")
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    @Column(name = "phone_e164", nullable = false, unique = true, length = 16)
    private String phoneE164;

    @Column(name = "profile_photo_key", length = 255)
    private String profilePhotoKey;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected LoginIdentity() {
    }

    public LoginIdentity(
            String passwordHash,
            String fullName,
            String phoneE164,
            String profilePhotoKey,
            LocalDate birthDate
    ) {
        this.passwordHash = requireText(passwordHash, "passwordHash");
        updateProfile(fullName, phoneE164, profilePhotoKey, birthDate);
    }

    public void changePasswordHash(String passwordHash) {
        this.passwordHash = requireText(passwordHash, "passwordHash");
    }

    public void updateProfile(
            String fullName,
            String phoneE164,
            String profilePhotoKey,
            LocalDate birthDate
    ) {
        this.fullName = requireText(fullName, "fullName");
        this.phoneE164 = normalisePhone(phoneE164);
        this.profilePhotoKey = normaliseOptionalText(profilePhotoKey);
        this.birthDate = birthDate;
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }

    public UUID getId() {
        return id;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getFullName() {
        return fullName;
    }

    public String getPhoneE164() {
        return phoneE164;
    }

    public String getProfilePhotoKey() {
        return profilePhotoKey;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public static String normalisePhone(String value) {
        String normalised = requireText(value, "phoneE164")
                .replace(" ", "")
                .replace("-", "")
                .replace("(", "")
                .replace(")", "");
        if (!normalised.matches("^\\+[1-9][0-9]{7,14}$")) {
            throw new IllegalArgumentException("phoneE164 must use international E.164 format");
        }
        return normalised;
    }

    private static String normaliseOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String normalised = value.trim();
        return normalised.isEmpty() ? null : normalised;
    }

    private static String requireText(String value, String fieldName) {
        String normalised = Objects.requireNonNull(value, fieldName + " must not be null").trim();
        if (normalised.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return normalised;
    }
}
