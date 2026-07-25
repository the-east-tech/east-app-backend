package com.eastapp.backend.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "users")
public class UserAccount {

    @Id
    @Generated
    @ColumnDefault("uuidv7()")
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false, updatable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "identity_id", nullable = false, updatable = false)
    private LoginIdentity identity;

    @Column(name = "employee_id", nullable = false, length = 32, updatable = false)
    private String employeeId;

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    @Column(name = "phone_e164", nullable = false, length = 16)
    private String phoneE164;

    @Column(name = "profile_photo_key", length = 255)
    private String profilePhotoKey;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @Column(nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserAccount() {
    }

    public UserAccount(
            Tenant tenant,
            LoginIdentity identity,
            String employeeId,
            String fullName,
            String phoneE164,
            Role role
    ) {
        this.tenant = Objects.requireNonNull(tenant, "tenant must not be null");
        this.identity = Objects.requireNonNull(identity, "identity must not be null");
        this.employeeId = normaliseEmployeeId(employeeId);
        this.fullName = requireText(fullName, "fullName");
        this.phoneE164 = normalisePhone(phoneE164);
        this.role = Objects.requireNonNull(role, "role must not be null");
    }

    public void updateProfile(
            String fullName,
            String phoneE164,
            String profilePhotoKey,
            LocalDate birthDate,
            LocalDate startDate,
            LocalDate endDate
    ) {
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("endDate must not be before startDate");
        }
        this.fullName = requireText(fullName, "fullName");
        this.phoneE164 = normalisePhone(phoneE164);
        this.profilePhotoKey = normaliseOptionalText(profilePhotoKey);
        this.birthDate = birthDate;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public void assignRole(Role role) {
        this.role = Objects.requireNonNull(role, "role must not be null");
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

    public Tenant getTenant() {
        return tenant;
    }

    public LoginIdentity getIdentity() {
        return identity;
    }

    public String getEmployeeId() {
        return employeeId;
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

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public Role getRole() {
        return role;
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

    public static String normaliseEmployeeId(String value) {
        return requireText(value, "employeeId").toUpperCase(Locale.ROOT);
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
