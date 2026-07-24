ALTER TABLE users
    ADD CONSTRAINT uq_users_tenant_id_id
        UNIQUE (tenant_id, id);

ALTER TABLE user_sessions
    ADD CONSTRAINT uq_user_sessions_user_id_id
        UNIQUE (user_id, id);

CREATE TABLE attendance_events (
    id UUID PRIMARY KEY DEFAULT uuidv7(),

    tenant_id UUID NOT NULL,
    user_id UUID NOT NULL,
    user_session_id UUID,
    client_event_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(16) NOT NULL,

    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    device_captured_at TIMESTAMPTZ NOT NULL,

    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    accuracy_meters DOUBLE PRECISION NOT NULL,

    work_location_name VARCHAR(120) NOT NULL,
    work_location_latitude DOUBLE PRECISION NOT NULL,
    work_location_longitude DOUBLE PRECISION NOT NULL,
    allowed_radius_meters INTEGER NOT NULL,
    distance_meters DOUBLE PRECISION NOT NULL,
    within_geofence BOOLEAN NOT NULL,

    camera_capture_valid BOOLEAN NOT NULL,
    face_valid BOOLEAN NOT NULL,
    face_count SMALLINT NOT NULL,
    face_box_width DOUBLE PRECISION,
    face_box_height DOUBLE PRECISION,
    face_yaw DOUBLE PRECISION,
    face_roll DOUBLE PRECISION,
    face_pitch DOUBLE PRECISION,
    qr_checkpoint_valid BOOLEAN NOT NULL,

    device_platform VARCHAR(32) NOT NULL,
    device_os_version VARCHAR(160),
    app_version VARCHAR(40) NOT NULL,
    validation_method VARCHAR(64) NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_attendance_events_tenant
        FOREIGN KEY (tenant_id)
        REFERENCES tenants (id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_attendance_events_user_same_tenant
        FOREIGN KEY (tenant_id, user_id)
        REFERENCES users (tenant_id, id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_attendance_events_session_same_user
        FOREIGN KEY (user_id, user_session_id)
        REFERENCES user_sessions (user_id, id)
        ON DELETE RESTRICT,

    CONSTRAINT uq_attendance_events_tenant_client_event
        UNIQUE (tenant_id, client_event_id),

    CONSTRAINT ck_attendance_events_type
        CHECK (event_type IN ('CLOCK_IN', 'CLOCK_OUT')),

    CONSTRAINT ck_attendance_events_client_event_not_blank
        CHECK (btrim(client_event_id) <> ''),

    CONSTRAINT ck_attendance_events_latitude
        CHECK (latitude BETWEEN -90 AND 90),

    CONSTRAINT ck_attendance_events_longitude
        CHECK (longitude BETWEEN -180 AND 180),

    CONSTRAINT ck_attendance_events_accuracy
        CHECK (accuracy_meters >= 0),

    CONSTRAINT ck_attendance_events_work_location_latitude
        CHECK (work_location_latitude BETWEEN -90 AND 90),

    CONSTRAINT ck_attendance_events_work_location_longitude
        CHECK (work_location_longitude BETWEEN -180 AND 180),

    CONSTRAINT ck_attendance_events_allowed_radius
        CHECK (allowed_radius_meters > 0),

    CONSTRAINT ck_attendance_events_distance
        CHECK (distance_meters >= 0),

    CONSTRAINT ck_attendance_events_face_count
        CHECK (face_count >= 0),

    CONSTRAINT ck_attendance_events_device_platform_not_blank
        CHECK (btrim(device_platform) <> ''),

    CONSTRAINT ck_attendance_events_app_version_not_blank
        CHECK (btrim(app_version) <> ''),

    CONSTRAINT ck_attendance_events_validation_method_not_blank
        CHECK (btrim(validation_method) <> '')
);

CREATE INDEX ix_attendance_events_tenant_occurred_at
    ON attendance_events (tenant_id, occurred_at DESC);

CREATE INDEX ix_attendance_events_user_occurred_at
    ON attendance_events (user_id, occurred_at DESC);
