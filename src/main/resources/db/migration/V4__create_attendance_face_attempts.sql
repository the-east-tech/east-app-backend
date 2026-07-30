CREATE TABLE attendance_face_attempts (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    tenant_id UUID NOT NULL,
    user_id UUID NOT NULL,
    user_session_id UUID,
    client_attempt_id VARCHAR(64) NOT NULL,
    intended_event_type VARCHAR(16) NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    device_attempted_at TIMESTAMPTZ NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    accuracy_meters DOUBLE PRECISION NOT NULL,
    captured_address VARCHAR(500) NOT NULL,
    work_location_name VARCHAR(200) NOT NULL,
    work_location_address VARCHAR(500) NOT NULL,
    work_location_latitude DOUBLE PRECISION NOT NULL,
    work_location_longitude DOUBLE PRECISION NOT NULL,
    distance_meters DOUBLE PRECISION NOT NULL,
    failure_reason VARCHAR(500) NOT NULL,
    face_count INTEGER NOT NULL,
    face_attempt_number INTEGER NOT NULL,
    face_box_width DOUBLE PRECISION,
    face_box_height DOUBLE PRECISION,
    face_yaw DOUBLE PRECISION,
    face_roll DOUBLE PRECISION,
    face_pitch DOUBLE PRECISION,
    device_platform VARCHAR(32) NOT NULL,
    device_os_version VARCHAR(160),
    app_version VARCHAR(40) NOT NULL,
    validation_method VARCHAR(64) NOT NULL,
    photo_content_type VARCHAR(40),
    photo_size_bytes BIGINT NOT NULL DEFAULT 0,
    photo_bytes BYTEA,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_attendance_face_attempts_tenant FOREIGN KEY (tenant_id)
        REFERENCES tenants (id) ON DELETE RESTRICT,
    CONSTRAINT fk_attendance_face_attempts_user_same_tenant FOREIGN KEY (tenant_id, user_id)
        REFERENCES users (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_attendance_face_attempts_session FOREIGN KEY (user_session_id)
        REFERENCES user_sessions (id) ON DELETE RESTRICT,
    CONSTRAINT uq_attendance_face_attempts_tenant_client UNIQUE (tenant_id, client_attempt_id),
    CONSTRAINT ck_attendance_face_attempts_client_not_blank CHECK (btrim(client_attempt_id) <> ''),
    CONSTRAINT ck_attendance_face_attempts_event_type CHECK (intended_event_type IN ('CLOCK_IN', 'CLOCK_OUT')),
    CONSTRAINT ck_attendance_face_attempts_latitude CHECK (latitude BETWEEN -90 AND 90),
    CONSTRAINT ck_attendance_face_attempts_longitude CHECK (longitude BETWEEN -180 AND 180),
    CONSTRAINT ck_attendance_face_attempts_accuracy CHECK (accuracy_meters >= 0),
    CONSTRAINT ck_attendance_face_attempts_captured_address CHECK (btrim(captured_address) <> ''),
    CONSTRAINT ck_attendance_face_attempts_work_location_name CHECK (btrim(work_location_name) <> ''),
    CONSTRAINT ck_attendance_face_attempts_work_location_address CHECK (btrim(work_location_address) <> ''),
    CONSTRAINT ck_attendance_face_attempts_work_latitude CHECK (work_location_latitude BETWEEN -90 AND 90),
    CONSTRAINT ck_attendance_face_attempts_work_longitude CHECK (work_location_longitude BETWEEN -180 AND 180),
    CONSTRAINT ck_attendance_face_attempts_distance CHECK (distance_meters >= 0),
    CONSTRAINT ck_attendance_face_attempts_failure_reason CHECK (btrim(failure_reason) <> ''),
    CONSTRAINT ck_attendance_face_attempts_face_count CHECK (face_count >= 0),
    CONSTRAINT ck_attendance_face_attempts_number CHECK (face_attempt_number BETWEEN 1 AND 3),
    CONSTRAINT ck_attendance_face_attempts_device_platform CHECK (btrim(device_platform) <> ''),
    CONSTRAINT ck_attendance_face_attempts_app_version CHECK (btrim(app_version) <> ''),
    CONSTRAINT ck_attendance_face_attempts_validation_method CHECK (btrim(validation_method) <> ''),
    CONSTRAINT ck_attendance_face_attempts_photo_size CHECK (photo_size_bytes BETWEEN 0 AND 5242880),
    CONSTRAINT ck_attendance_face_attempts_photo_consistency CHECK (
        (photo_size_bytes = 0 AND photo_content_type IS NULL AND photo_bytes IS NULL)
        OR
        (photo_size_bytes > 0
            AND photo_content_type IN ('image/jpeg', 'image/png')
            AND photo_bytes IS NOT NULL
            AND octet_length(photo_bytes) = photo_size_bytes)
    )
);

CREATE INDEX ix_attendance_face_attempts_tenant_recorded
    ON attendance_face_attempts (tenant_id, recorded_at DESC);
CREATE INDEX ix_attendance_face_attempts_user_attempted
    ON attendance_face_attempts (user_id, device_attempted_at DESC);
