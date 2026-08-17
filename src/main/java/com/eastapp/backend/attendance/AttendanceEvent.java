package com.eastapp.backend.attendance;

import com.eastapp.backend.auth.UserSession;
import com.eastapp.backend.organisation.Tenant;
import com.eastapp.backend.people.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Generated;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "attendance_events")
public class AttendanceEvent {

    @Id
    @Generated
    @ColumnDefault("uuidv7()")
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false, updatable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private UserAccount userAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_session_id", updatable = false)
    private UserSession userSession;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "qr_code_id", nullable = false, updatable = false)
    private AttendanceQrCode qrCode;

    @Column(name = "client_event_id", nullable = false, length = 64, updatable = false)
    private String clientEventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 16, updatable = false)
    private AttendanceEventType eventType;

    @CreationTimestamp
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "device_captured_at", nullable = false, updatable = false)
    private Instant deviceCapturedAt;

    @Column(nullable = false, updatable = false)
    private double latitude;

    @Column(nullable = false, updatable = false)
    private double longitude;

    @Column(name = "accuracy_meters", nullable = false, updatable = false)
    private double accuracyMeters;

    @Column(name = "captured_address", nullable = false, length = 500, updatable = false)
    private String capturedAddress;

    @Column(name = "work_location_name", nullable = false, length = 200, updatable = false)
    private String workLocationName;

    @Column(name = "work_location_address", nullable = false, length = 500, updatable = false)
    private String workLocationAddress;

    @Column(name = "work_location_latitude", nullable = false, updatable = false)
    private double workLocationLatitude;

    @Column(name = "work_location_longitude", nullable = false, updatable = false)
    private double workLocationLongitude;

    @Column(name = "distance_meters", nullable = false, updatable = false)
    private double distanceMeters;

    @Column(name = "device_platform", nullable = false, length = 32, updatable = false)
    private String devicePlatform;

    @Column(name = "device_os_version", length = 160, updatable = false)
    private String deviceOsVersion;

    @Column(name = "app_version", nullable = false, length = 40, updatable = false)
    private String appVersion;

    @Column(name = "validation_method", nullable = false, length = 64, updatable = false)
    private String validationMethod;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AttendanceEvent() {
    }

    public AttendanceEvent(
            Tenant tenant,
            UserAccount userAccount,
            UserSession userSession,
            AttendanceQrCode qrCode,
            String clientEventId,
            AttendanceEventType eventType,
            Instant deviceCapturedAt,
            double latitude,
            double longitude,
            double accuracyMeters,
            String capturedAddress,
            String workLocationName,
            String workLocationAddress,
            double workLocationLatitude,
            double workLocationLongitude,
            double distanceMeters,
            String devicePlatform,
            String deviceOsVersion,
            String appVersion,
            String validationMethod
    ) {
        this.tenant = Objects.requireNonNull(tenant, "tenant must not be null");
        this.userAccount = Objects.requireNonNull(userAccount, "userAccount must not be null");
        this.userSession = userSession;
        this.qrCode = Objects.requireNonNull(qrCode, "qrCode must not be null");
        this.clientEventId = requireText(clientEventId, "clientEventId");
        this.eventType = Objects.requireNonNull(eventType, "eventType must not be null");
        this.deviceCapturedAt = Objects.requireNonNull(deviceCapturedAt, "deviceCapturedAt must not be null");
        this.latitude = latitude;
        this.longitude = longitude;
        this.accuracyMeters = accuracyMeters;
        this.capturedAddress = requireText(capturedAddress, "capturedAddress");
        this.workLocationName = requireText(workLocationName, "workLocationName");
        this.workLocationAddress = requireText(workLocationAddress, "workLocationAddress");
        this.workLocationLatitude = workLocationLatitude;
        this.workLocationLongitude = workLocationLongitude;
        this.distanceMeters = distanceMeters;
        this.devicePlatform = requireText(devicePlatform, "devicePlatform");
        this.deviceOsVersion = normaliseOptionalText(deviceOsVersion);
        this.appVersion = requireText(appVersion, "appVersion");
        this.validationMethod = requireText(validationMethod, "validationMethod");
    }

    public UUID getId() { return id; }
    public Tenant getTenant() { return tenant; }
    public UserAccount getUserAccount() { return userAccount; }
    public UserSession getUserSession() { return userSession; }
    public AttendanceQrCode getQrCode() { return qrCode; }
    public String getClientEventId() { return clientEventId; }
    public AttendanceEventType getEventType() { return eventType; }
    public Instant getOccurredAt() { return occurredAt; }
    public Instant getDeviceCapturedAt() { return deviceCapturedAt; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public double getAccuracyMeters() { return accuracyMeters; }
    public String getCapturedAddress() { return capturedAddress; }
    public String getWorkLocationName() { return workLocationName; }
    public String getWorkLocationAddress() { return workLocationAddress; }
    public double getWorkLocationLatitude() { return workLocationLatitude; }
    public double getWorkLocationLongitude() { return workLocationLongitude; }
    public double getDistanceMeters() { return distanceMeters; }
    public String getDevicePlatform() { return devicePlatform; }
    public String getDeviceOsVersion() { return deviceOsVersion; }
    public String getAppVersion() { return appVersion; }
    public String getValidationMethod() { return validationMethod; }
    public Instant getCreatedAt() { return createdAt; }

    private static String normaliseOptionalText(String value) {
        if (value == null) return null;
        String normalised = value.trim();
        return normalised.isEmpty() ? null : normalised;
    }

    private static String requireText(String value, String fieldName) {
        String normalised = Objects.requireNonNull(value, fieldName + " must not be null").trim();
        if (normalised.isEmpty()) throw new IllegalArgumentException(fieldName + " must not be blank");
        return normalised;
    }
}
