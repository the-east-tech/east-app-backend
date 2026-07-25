package com.eastapp.backend.organisation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "tenants")
public class Tenant {

    @Id
    @Generated
    @ColumnDefault("uuidv7()")
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "company_code", nullable = false, length = 32, updatable = false)
    private String companyCode;

    @Column(name = "business_name", nullable = false, length = 120)
    private String businessName;

    @Column(name = "employee_id_prefix", nullable = false, length = 3, updatable = false)
    private String employeeIdPrefix;

    @Column(name = "next_employee_number", nullable = false)
    private long nextEmployeeNumber = 1L;

    @Column(nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Tenant() {
    }

    public Tenant(String companyCode, String businessName, String employeeIdPrefix) {
        this.companyCode = normaliseCode(companyCode);
        this.businessName = requireText(businessName, "businessName");
        this.employeeIdPrefix = normaliseEmployeeIdPrefix(employeeIdPrefix);
    }

    public String allocateEmployeeId() {
        if (nextEmployeeNumber < 1) {
            throw new IllegalStateException("nextEmployeeNumber must be positive");
        }
        String employeeId = employeeIdPrefix + String.format(Locale.ROOT, "%04d", nextEmployeeNumber);
        nextEmployeeNumber++;
        return employeeId;
    }

    public String previewNextEmployeeId() {
        return employeeIdPrefix + String.format(Locale.ROOT, "%04d", nextEmployeeNumber);
    }

    public void update(String businessName, boolean active) {
        this.businessName = requireText(businessName, "businessName");
        this.active = active;
    }

    public UUID getId() {
        return id;
    }

    public String getCompanyCode() {
        return companyCode;
    }

    public String getBusinessName() {
        return businessName;
    }

    public String getEmployeeIdPrefix() {
        return employeeIdPrefix;
    }

    public long getNextEmployeeNumber() {
        return nextEmployeeNumber;
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

    public static String normaliseCode(String value) {
        return requireText(value, "companyCode").toUpperCase(Locale.ROOT);
    }

    public static String normaliseEmployeeIdPrefix(String value) {
        String normalised = requireText(value, "employeeIdPrefix").toUpperCase(Locale.ROOT);
        if (!normalised.matches("^[A-Z]{1,3}$")) {
            throw new IllegalArgumentException("employeeIdPrefix must contain 1 to 3 letters");
        }
        return normalised;
    }

    private static String requireText(String value, String fieldName) {
        String normalised = Objects.requireNonNull(value, fieldName + " must not be null").trim();
        if (normalised.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return normalised;
    }
}
