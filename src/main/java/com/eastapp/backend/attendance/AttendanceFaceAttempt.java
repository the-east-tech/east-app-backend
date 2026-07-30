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
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "attendance_face_attempts")
public class AttendanceFaceAttempt {

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

    @Column(name = "client_attempt_id", nullable = false, length = 64, updatable = false)
    private String clientAttemptId;

    @Enumerated(EnumType.STRING)
    @Column(name = "intended_event_type", nullable = false, length = 16, updatable = false)
    private AttendanceEventType intendedEventType;

    @CreationTimestamp
    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    @Column(name = "device_attempted_at", nullable = false, updatable = false)
    private Instant deviceAttemptedAt;

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

    @Column(name = "failure_reason", nullable = false, length = 500, updatable = false)
    private String failureReason;

    @Column(name = "face_count", nullable = false, updatable = false)
    private int faceCount;

    @Column(name = "face_attempt_number", nullable = false, updatable = false)
    private int faceAttemptNumber;

    @Column(name = "face_box_width", updatable = false)
    private Double faceBoxWidth;

    @Column(name = "face_box_height", updatable = false)
    private Double faceBoxHeight;

    @Column(name = "face_yaw", updatable = false)
    private Double faceYaw;

    @Column(name = "face_roll", updatable = false)
    private Double faceRoll;

    @Column(name = "face_pitch", updatable = false)
    private Double facePitch;

    @Column(name = "device_platform", nullable = false, length = 32, updatable = false)
    private String devicePlatform;

    @Column(name = "device_os_version", length = 160, updatable = false)
    private String deviceOsVersion;

    @Column(name = "app_version", nullable = false, length = 40, updatable = false)
    private String appVersion;

    @Column(name = "validation_method", nullable = false, length = 64, updatable = false)
    private String validationMethod;

    @Column(name = "photo_content_type", length = 40, updatable = false)
    private String photoContentType;

    @Column(name = "photo_size_bytes", nullable = false, updatable = false)
    private long photoSizeBytes;

    @Column(name = "photo_bytes", updatable = false, columnDefinition = "bytea")
    private byte[] photoBytes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AttendanceFaceAttempt() {
    }

    public AttendanceFaceAttempt(
            Tenant tenant,
            UserAccount userAccount,
            UserSession userSession,
            String clientAttemptId,
            AttendanceEventType intendedEventType,
            Instant deviceAttemptedAt,
            double latitude,
            double longitude,
            double accuracyMeters,
            String capturedAddress,
            String workLocationName,
            String workLocationAddress,
            double workLocationLatitude,
            double workLocationLongitude,
            double distanceMeters,
            String failureReason,
            int faceCount,
            int faceAttemptNumber,
            Double faceBoxWidth,
            Double faceBoxHeight,
            Double faceYaw,
            Double faceRoll,
            Double facePitch,
            String devicePlatform,
            String deviceOsVersion,
            String appVersion,
            String validationMethod,
            String photoContentType,
            byte[] photoBytes
    ) {
        this.tenant = Objects.requireNonNull(tenant);
        this.userAccount = Objects.requireNonNull(userAccount);
        this.userSession = userSession;
        this.clientAttemptId = requireText(clientAttemptId, "clientAttemptId");
        this.intendedEventType = Objects.requireNonNull(intendedEventType);
        this.deviceAttemptedAt = Objects.requireNonNull(deviceAttemptedAt);
        this.latitude = latitude;
        this.longitude = longitude;
        this.accuracyMeters = accuracyMeters;
        this.capturedAddress = requireText(capturedAddress, "capturedAddress");
        this.workLocationName = requireText(workLocationName, "workLocationName");
        this.workLocationAddress = requireText(workLocationAddress, "workLocationAddress");
        this.workLocationLatitude = workLocationLatitude;
        this.workLocationLongitude = workLocationLongitude;
        this.distanceMeters = distanceMeters;
        this.failureReason = requireText(failureReason, "failureReason");
        this.faceCount = faceCount;
        this.faceAttemptNumber = faceAttemptNumber;
        this.faceBoxWidth = faceBoxWidth;
        this.faceBoxHeight = faceBoxHeight;
        this.faceYaw = faceYaw;
        this.faceRoll = faceRoll;
        this.facePitch = facePitch;
        this.devicePlatform = requireText(devicePlatform, "devicePlatform");
        this.deviceOsVersion = normaliseOptionalText(deviceOsVersion);
        this.appVersion = requireText(appVersion, "appVersion");
        this.validationMethod = requireText(validationMethod, "validationMethod");
        this.photoContentType = normaliseOptionalText(photoContentType);
        this.photoBytes = photoBytes == null ? null : Arrays.copyOf(photoBytes, photoBytes.length);
        this.photoSizeBytes = this.photoBytes == null ? 0 : this.photoBytes.length;
    }

    public UUID getId() { return id; }
    public Tenant getTenant() { return tenant; }
    public UserAccount getUserAccount() { return userAccount; }
    public UserSession getUserSession() { return userSession; }
    public String getClientAttemptId() { return clientAttemptId; }
    public AttendanceEventType getIntendedEventType() { return intendedEventType; }
    public Instant getRecordedAt() { return recordedAt; }
    public Instant getDeviceAttemptedAt() { return deviceAttemptedAt; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public double getAccuracyMeters() { return accuracyMeters; }
    public String getCapturedAddress() { return capturedAddress; }
    public String getWorkLocationName() { return workLocationName; }
    public String getWorkLocationAddress() { return workLocationAddress; }
    public double getWorkLocationLatitude() { return workLocationLatitude; }
    public double getWorkLocationLongitude() { return workLocationLongitude; }
    public double getDistanceMeters() { return distanceMeters; }
    public String getFailureReason() { return failureReason; }
    public int getFaceCount() { return faceCount; }
    public int getFaceAttemptNumber() { return faceAttemptNumber; }
    public Double getFaceBoxWidth() { return faceBoxWidth; }
    public Double getFaceBoxHeight() { return faceBoxHeight; }
    public Double getFaceYaw() { return faceYaw; }
    public Double getFaceRoll() { return faceRoll; }
    public Double getFacePitch() { return facePitch; }
    public String getDevicePlatform() { return devicePlatform; }
    public String getDeviceOsVersion() { return deviceOsVersion; }
    public String getAppVersion() { return appVersion; }
    public String getValidationMethod() { return validationMethod; }
    public String getPhotoContentType() { return photoContentType; }
    public long getPhotoSizeBytes() { return photoSizeBytes; }
    public byte[] getPhotoBytes() { return photoBytes == null ? null : Arrays.copyOf(photoBytes, photoBytes.length); }
    public boolean hasPhoto() { return photoBytes != null && photoBytes.length > 0; }
    public Instant getCreatedAt() { return createdAt; }

    private static String normaliseOptionalText(String value) {
        if (value == null) return null;
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
