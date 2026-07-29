package com.eastapp.backend.people;

import com.eastapp.backend.auth.LoginIdentity;
import com.eastapp.backend.organisation.Tenant;
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

/**
 * Tenant-scoped employment membership for one global login identity.
 */
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

    @Column(name = "employee_id", nullable = false, length = 32)
    private String employeeId;

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
            Role role
    ) {
        this.tenant = Objects.requireNonNull(tenant, "tenant must not be null");
        this.identity = Objects.requireNonNull(identity, "identity must not be null");
        this.employeeId = normaliseEmployeeId(employeeId);
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
        identity.updateProfile(fullName, phoneE164, profilePhotoKey, birthDate);
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
        return identity.getFullName();
    }

    public String getPhoneE164() {
        return identity.getPhoneE164();
    }

    public String getProfilePhotoKey() {
        return identity.getProfilePhotoKey();
    }

    public LocalDate getBirthDate() {
        return identity.getBirthDate();
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
        return LoginIdentity.normalisePhone(value);
    }

    private static String requireText(String value, String fieldName) {
        String normalised = Objects.requireNonNull(value, fieldName + " must not be null").trim();
        if (normalised.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return normalised;
    }
}
