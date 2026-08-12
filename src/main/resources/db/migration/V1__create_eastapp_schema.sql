-- EastApp clean development baseline (v052).
-- This single V1 file defines the complete current schema after the deliberate
-- database reset. From this baseline onward, keep V1 immutable and add V2+ migrations.

CREATE TABLE application_setup (
    id SMALLINT PRIMARY KEY,
    setup_code_hash BYTEA,
    setup_code_expires_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_application_setup_singleton CHECK (id = 1),
    CONSTRAINT ck_application_setup_code_hash_length CHECK (
        setup_code_hash IS NULL OR octet_length(setup_code_hash) = 32
    ),
    CONSTRAINT ck_application_setup_code_pair CHECK (
        (setup_code_hash IS NULL) = (setup_code_expires_at IS NULL)
    )
);

INSERT INTO application_setup (id) VALUES (1);

CREATE TABLE tenants (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    company_code VARCHAR(32) NOT NULL,
    business_name VARCHAR(120) NOT NULL,
    employee_id_prefix VARCHAR(3) NOT NULL,
    next_employee_number BIGINT NOT NULL DEFAULT 1,
    google_place_id VARCHAR(255) NOT NULL,
    google_place_name VARCHAR(200) NOT NULL,
    formatted_address VARCHAR(500) NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    google_maps_uri VARCHAR(500),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_tenants_company_code UNIQUE (company_code),
    CONSTRAINT uq_tenants_employee_id_prefix UNIQUE (employee_id_prefix),
    CONSTRAINT ck_tenants_company_code_uppercase CHECK (company_code = upper(company_code)),
    CONSTRAINT ck_tenants_company_code_format CHECK (company_code ~ '^[A-Z0-9][A-Z0-9_-]{1,31}$'),
    CONSTRAINT ck_tenants_business_name_not_blank CHECK (btrim(business_name) <> ''),
    CONSTRAINT ck_tenants_employee_id_prefix_uppercase CHECK (employee_id_prefix = upper(employee_id_prefix)),
    CONSTRAINT ck_tenants_employee_id_prefix_format CHECK (employee_id_prefix ~ '^[A-Z]{1,3}$'),
    CONSTRAINT ck_tenants_next_employee_number CHECK (next_employee_number >= 1),
    CONSTRAINT ck_tenants_google_place_id_not_blank CHECK (btrim(google_place_id) <> ''),
    CONSTRAINT ck_tenants_google_place_name_not_blank CHECK (btrim(google_place_name) <> ''),
    CONSTRAINT ck_tenants_formatted_address_not_blank CHECK (btrim(formatted_address) <> ''),
    CONSTRAINT ck_tenants_latitude CHECK (latitude BETWEEN -90 AND 90),
    CONSTRAINT ck_tenants_longitude CHECK (longitude BETWEEN -180 AND 180),
    CONSTRAINT ck_tenants_google_maps_uri_not_blank CHECK (google_maps_uri IS NULL OR btrim(google_maps_uri) <> '')
);

CREATE TABLE roles (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    tenant_id UUID NOT NULL,
    system_key VARCHAR(32),
    name VARCHAR(80) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_roles_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id) ON DELETE RESTRICT,
    CONSTRAINT uq_roles_tenant_id_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_roles_tenant_system_key UNIQUE (tenant_id, system_key),
    CONSTRAINT ck_roles_system_key CHECK (
        system_key IS NULL OR system_key IN ('OWNER', 'HEAD', 'MANAGER', 'SUPERVISOR', 'STAFF_1', 'STAFF_2')
    ),
    CONSTRAINT ck_roles_name_not_blank CHECK (btrim(name) <> '')
);
CREATE UNIQUE INDEX uq_roles_tenant_name_ci ON roles (tenant_id, lower(name));

CREATE TABLE login_identities (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(120) NOT NULL,
    phone_e164 VARCHAR(16) NOT NULL,
    profile_photo_key VARCHAR(255),
    birth_date DATE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_login_identities_phone_e164 UNIQUE (phone_e164),
    CONSTRAINT ck_login_identities_password_hash_not_blank CHECK (btrim(password_hash) <> ''),
    CONSTRAINT ck_login_identities_full_name_not_blank CHECK (btrim(full_name) <> ''),
    CONSTRAINT ck_login_identities_phone_e164_format CHECK (phone_e164 ~ '^\+[1-9][0-9]{7,14}$'),
    CONSTRAINT ck_login_identities_profile_photo_key_not_blank CHECK (
        profile_photo_key IS NULL OR btrim(profile_photo_key) <> ''
    )
);

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    tenant_id UUID NOT NULL,
    identity_id UUID NOT NULL,
    employee_id VARCHAR(32) NOT NULL,
    start_date DATE,
    end_date DATE,
    role_id UUID NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_users_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id) ON DELETE RESTRICT,
    CONSTRAINT fk_users_login_identity FOREIGN KEY (identity_id) REFERENCES login_identities (id) ON DELETE RESTRICT,
    CONSTRAINT fk_users_role_same_tenant FOREIGN KEY (tenant_id, role_id) REFERENCES roles (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT uq_users_tenant_id_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_users_identity_id UNIQUE (identity_id, id),
    CONSTRAINT uq_users_tenant_employee_id UNIQUE (tenant_id, employee_id),
    CONSTRAINT uq_users_identity_tenant UNIQUE (identity_id, tenant_id),
    CONSTRAINT ck_users_employee_id_uppercase CHECK (employee_id = upper(employee_id)),
    CONSTRAINT ck_users_employee_id_format CHECK (employee_id ~ '^[A-Z0-9][A-Z0-9_-]{1,31}$'),
    CONSTRAINT ck_users_employment_dates CHECK (end_date IS NULL OR start_date IS NULL OR end_date >= start_date)
);
CREATE INDEX ix_users_identity_id ON users (identity_id);
CREATE INDEX ix_users_tenant_active ON users (tenant_id, active);

CREATE TABLE user_sessions (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    identity_id UUID NOT NULL,
    active_user_id UUID NOT NULL,
    token_hash BYTEA NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_used_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at TIMESTAMPTZ,
    CONSTRAINT fk_user_sessions_identity FOREIGN KEY (identity_id) REFERENCES login_identities (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_sessions_active_user_identity FOREIGN KEY (identity_id, active_user_id)
        REFERENCES users (identity_id, id) ON DELETE RESTRICT,
    CONSTRAINT uq_user_sessions_token_hash UNIQUE (token_hash),
    CONSTRAINT ck_user_sessions_token_hash_length CHECK (octet_length(token_hash) = 32),
    CONSTRAINT ck_user_sessions_revoked_at CHECK (revoked_at IS NULL OR revoked_at >= created_at)
);
CREATE INDEX ix_user_sessions_identity_id ON user_sessions (identity_id);

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
    captured_address VARCHAR(500) NOT NULL,
    work_location_name VARCHAR(200) NOT NULL,
    work_location_address VARCHAR(500) NOT NULL,
    work_location_latitude DOUBLE PRECISION NOT NULL,
    work_location_longitude DOUBLE PRECISION NOT NULL,
    distance_meters DOUBLE PRECISION NOT NULL,
    camera_capture_valid BOOLEAN NOT NULL,
    face_valid BOOLEAN NOT NULL,
    face_count INTEGER NOT NULL,
    face_attempt_count INTEGER NOT NULL,
    face_verification_bypassed BOOLEAN NOT NULL,
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
    CONSTRAINT fk_attendance_events_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id) ON DELETE RESTRICT,
    CONSTRAINT fk_attendance_events_user_same_tenant FOREIGN KEY (tenant_id, user_id)
        REFERENCES users (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_attendance_events_session FOREIGN KEY (user_session_id)
        REFERENCES user_sessions (id) ON DELETE RESTRICT,
    CONSTRAINT uq_attendance_events_tenant_client_event UNIQUE (tenant_id, client_event_id),
    CONSTRAINT ck_attendance_events_type CHECK (event_type IN ('CLOCK_IN', 'CLOCK_OUT')),
    CONSTRAINT ck_attendance_events_client_event_not_blank CHECK (btrim(client_event_id) <> ''),
    CONSTRAINT ck_attendance_events_latitude CHECK (latitude BETWEEN -90 AND 90),
    CONSTRAINT ck_attendance_events_longitude CHECK (longitude BETWEEN -180 AND 180),
    CONSTRAINT ck_attendance_events_accuracy CHECK (accuracy_meters >= 0),
    CONSTRAINT ck_attendance_events_captured_address_not_blank CHECK (btrim(captured_address) <> ''),
    CONSTRAINT ck_attendance_events_work_location_name_not_blank CHECK (btrim(work_location_name) <> ''),
    CONSTRAINT ck_attendance_events_work_location_address_not_blank CHECK (btrim(work_location_address) <> ''),
    CONSTRAINT ck_attendance_events_work_location_latitude CHECK (work_location_latitude BETWEEN -90 AND 90),
    CONSTRAINT ck_attendance_events_work_location_longitude CHECK (work_location_longitude BETWEEN -180 AND 180),
    CONSTRAINT ck_attendance_events_distance CHECK (distance_meters >= 0),
    CONSTRAINT ck_attendance_events_face_count CHECK (face_count >= 0),
    CONSTRAINT ck_attendance_events_face_attempt_count CHECK (face_attempt_count BETWEEN 1 AND 3),
    CONSTRAINT ck_attendance_events_face_result CHECK (
        (face_valid = TRUE AND face_count = 1 AND face_verification_bypassed = FALSE)
        OR
        (face_valid = FALSE AND face_attempt_count = 3 AND face_verification_bypassed = TRUE)
    ),
    CONSTRAINT ck_attendance_events_device_platform_not_blank CHECK (btrim(device_platform) <> ''),
    CONSTRAINT ck_attendance_events_app_version_not_blank CHECK (btrim(app_version) <> ''),
    CONSTRAINT ck_attendance_events_validation_method_not_blank CHECK (btrim(validation_method) <> '')
);
CREATE INDEX ix_attendance_events_tenant_occurred_at ON attendance_events (tenant_id, occurred_at DESC);
CREATE INDEX ix_attendance_events_user_occurred_at ON attendance_events (user_id, occurred_at DESC);

CREATE TABLE stock_media (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    tenant_id UUID NOT NULL,
    storage_key VARCHAR(80) NOT NULL,
    content_type VARCHAR(40) NOT NULL,
    size_bytes BIGINT NOT NULL,
    content_bytes BYTEA NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_stock_media_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id) ON DELETE RESTRICT,
    CONSTRAINT uq_stock_media_tenant_id_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_stock_media_tenant_key UNIQUE (tenant_id, storage_key),
    CONSTRAINT ck_stock_media_size_positive CHECK (size_bytes > 0),
    CONSTRAINT ck_stock_media_size_limit CHECK (size_bytes <= 5242880)
);
CREATE INDEX ix_stock_media_tenant_created_at ON stock_media (tenant_id, created_at DESC);

CREATE TABLE stock_tags (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    tenant_id UUID NOT NULL,
    tag VARCHAR(80) NOT NULL,
    created_by_user_id UUID NOT NULL,
    updated_by_user_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_stock_tags_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id) ON DELETE RESTRICT,
    CONSTRAINT fk_stock_tags_created_by FOREIGN KEY (tenant_id, created_by_user_id)
        REFERENCES users (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_stock_tags_updated_by FOREIGN KEY (tenant_id, updated_by_user_id)
        REFERENCES users (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT uq_stock_tags_tenant_id_id UNIQUE (tenant_id, id),
    CONSTRAINT ck_stock_tags_tag_not_blank CHECK (btrim(tag) <> '')
);

CREATE TABLE stock_suppliers (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    tenant_id UUID NOT NULL,
    supplier_name VARCHAR(120) NOT NULL,
    supplier_item VARCHAR(160) NOT NULL,
    contact_person VARCHAR(120) NOT NULL DEFAULT '',
    phone VARCHAR(32) NOT NULL DEFAULT '',
    address VARCHAR(500) NOT NULL DEFAULT '',
    notes VARCHAR(1000) NOT NULL DEFAULT '',
    unit VARCHAR(32) NOT NULL,
    recommended_purchase_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
    recommended_purchase_frequency VARCHAR(80) NOT NULL DEFAULT '',
    pricing_per_unit NUMERIC(14,2) NOT NULL DEFAULT 0,
    minimum_balance_value NUMERIC(14,2) NOT NULL DEFAULT 0,
    maximum_balance_value NUMERIC(14,2) NOT NULL DEFAULT 0,
    current_balance_value NUMERIC(14,2) NOT NULL DEFAULT 0,
    last_balance_updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_balance_updated_by_user_id UUID NOT NULL,
    created_by_user_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_stock_suppliers_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id) ON DELETE RESTRICT,
    CONSTRAINT fk_stock_suppliers_balance_user FOREIGN KEY (tenant_id, last_balance_updated_by_user_id)
        REFERENCES users (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_stock_suppliers_created_by FOREIGN KEY (tenant_id, created_by_user_id)
        REFERENCES users (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT uq_stock_suppliers_tenant_id_id UNIQUE (tenant_id, id),
    CONSTRAINT ck_stock_suppliers_name_not_blank CHECK (btrim(supplier_name) <> ''),
    CONSTRAINT ck_stock_suppliers_item_not_blank CHECK (btrim(supplier_item) <> ''),
    CONSTRAINT ck_stock_suppliers_unit_not_blank CHECK (btrim(unit) <> ''),
    CONSTRAINT ck_stock_suppliers_balances CHECK (
        minimum_balance_value >= 0 AND maximum_balance_value >= minimum_balance_value AND current_balance_value >= 0
    )
);

CREATE TABLE stock_skus (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    tenant_id UUID NOT NULL,
    name VARCHAR(120) NOT NULL,
    tag1_id UUID NOT NULL,
    tag2_id UUID NOT NULL,
    unit VARCHAR(32) NOT NULL,
    minimum_balance_value NUMERIC(14,2) NOT NULL DEFAULT 0,
    maximum_balance_value NUMERIC(14,2) NOT NULL DEFAULT 0,
    current_balance_value NUMERIC(14,2) NOT NULL DEFAULT 0,
    recovery_percent INTEGER NOT NULL DEFAULT 100,
    minimum_price_rm NUMERIC(14,2) NOT NULL DEFAULT 0,
    maximum_price_rm NUMERIC(14,2) NOT NULL DEFAULT 0,
    thumbnail_media_id UUID NOT NULL,
    stock_check_frequency_days INTEGER NOT NULL DEFAULT 1,
    reset_time TIME NOT NULL DEFAULT '08:00',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    cooling_period BOOLEAN NOT NULL DEFAULT TRUE,
    last_updated_by_user_id UUID NOT NULL,
    created_by_user_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_stock_skus_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id) ON DELETE RESTRICT,
    CONSTRAINT fk_stock_skus_tag1_same_tenant FOREIGN KEY (tenant_id, tag1_id)
        REFERENCES stock_tags (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_stock_skus_tag2_same_tenant FOREIGN KEY (tenant_id, tag2_id)
        REFERENCES stock_tags (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_stock_skus_thumbnail_same_tenant FOREIGN KEY (tenant_id, thumbnail_media_id)
        REFERENCES stock_media (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_stock_skus_updated_by FOREIGN KEY (tenant_id, last_updated_by_user_id)
        REFERENCES users (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_stock_skus_created_by FOREIGN KEY (tenant_id, created_by_user_id)
        REFERENCES users (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT uq_stock_skus_tenant_id_id UNIQUE (tenant_id, id),
    CONSTRAINT ck_stock_skus_name_not_blank CHECK (btrim(name) <> ''),
    CONSTRAINT ck_stock_skus_unit_not_blank CHECK (btrim(unit) <> ''),
    CONSTRAINT ck_stock_skus_balances CHECK (
        minimum_balance_value >= 0 AND maximum_balance_value >= minimum_balance_value AND current_balance_value >= 0
    ),
    CONSTRAINT ck_stock_skus_recovery CHECK (recovery_percent BETWEEN 1 AND 100),
    CONSTRAINT ck_stock_skus_prices CHECK (minimum_price_rm >= 0 AND maximum_price_rm >= minimum_price_rm),
    CONSTRAINT ck_stock_skus_frequency CHECK (stock_check_frequency_days > 0)
);
CREATE INDEX ix_stock_skus_tenant_tag1 ON stock_skus (tenant_id, tag1_id);
CREATE INDEX ix_stock_skus_tenant_tag2 ON stock_skus (tenant_id, tag2_id);

CREATE TABLE stock_sku_suppliers (
    sku_id UUID NOT NULL,
    supplier_id UUID NOT NULL,
    PRIMARY KEY (sku_id, supplier_id),
    CONSTRAINT fk_stock_sku_suppliers_sku FOREIGN KEY (sku_id) REFERENCES stock_skus (id) ON DELETE CASCADE,
    CONSTRAINT fk_stock_sku_suppliers_supplier FOREIGN KEY (supplier_id) REFERENCES stock_suppliers (id) ON DELETE RESTRICT
);

CREATE TABLE stock_sku_assignees (
    sku_id UUID NOT NULL,
    position INTEGER NOT NULL,
    assigned_staff_name VARCHAR(120) NOT NULL,
    PRIMARY KEY (sku_id, position),
    CONSTRAINT fk_stock_sku_assignees_sku FOREIGN KEY (sku_id) REFERENCES stock_skus (id) ON DELETE CASCADE,
    CONSTRAINT ck_stock_sku_assignees_position CHECK (position >= 0),
    CONSTRAINT ck_stock_sku_assignee_not_blank CHECK (btrim(assigned_staff_name) <> '')
);

CREATE TABLE stock_sku_receiving_checklist (
    sku_id UUID NOT NULL,
    position INTEGER NOT NULL,
    checklist_item VARCHAR(300) NOT NULL,
    PRIMARY KEY (sku_id, position),
    CONSTRAINT fk_stock_sku_checklist_sku FOREIGN KEY (sku_id) REFERENCES stock_skus (id) ON DELETE CASCADE,
    CONSTRAINT ck_stock_sku_checklist_position CHECK (position >= 0),
    CONSTRAINT ck_stock_sku_checklist_not_blank CHECK (btrim(checklist_item) <> '')
);

CREATE TABLE stock_count_submissions (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    tenant_id UUID NOT NULL,
    sku_id UUID NOT NULL,
    submitted_by_user_id UUID NOT NULL,
    captured_at TIMESTAMPTZ NOT NULL,
    count_cycle_started_at TIMESTAMPTZ NOT NULL,
    stock_photo_name VARCHAR(500) NOT NULL,
    invoice_photo_name VARCHAR(500) NOT NULL,
    previous_balance_value NUMERIC(14,2) NOT NULL,
    current_balance_value NUMERIC(14,2) NOT NULL,
    below_minimum_balance BOOLEAN NOT NULL,
    review_status VARCHAR(24) NOT NULL DEFAULT 'Pending Review',
    reviewed_by_user_id UUID,
    reviewed_at TIMESTAMPTZ,
    review_note VARCHAR(1000) NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_stock_counts_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id) ON DELETE RESTRICT,
    CONSTRAINT fk_stock_counts_sku_same_tenant FOREIGN KEY (tenant_id, sku_id)
        REFERENCES stock_skus (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_stock_counts_submitted_by FOREIGN KEY (tenant_id, submitted_by_user_id)
        REFERENCES users (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_stock_counts_reviewed_by FOREIGN KEY (tenant_id, reviewed_by_user_id)
        REFERENCES users (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT uq_stock_counts_tenant_id_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_stock_counts_tenant_sku_cycle UNIQUE (tenant_id, sku_id, count_cycle_started_at),
    CONSTRAINT ck_stock_counts_balances CHECK (previous_balance_value >= 0 AND current_balance_value >= 0),
    CONSTRAINT ck_stock_counts_review_status CHECK (review_status IN ('Pending Review', 'Approved', 'Rejected'))
);
CREATE INDEX ix_stock_counts_tenant_captured_at ON stock_count_submissions (tenant_id, captured_at DESC);

CREATE TABLE stock_count_submission_checks (
    submission_id UUID NOT NULL,
    check_key VARCHAR(120) NOT NULL,
    checked BOOLEAN NOT NULL,
    PRIMARY KEY (submission_id, check_key),
    CONSTRAINT fk_stock_count_checks_submission FOREIGN KEY (submission_id)
        REFERENCES stock_count_submissions (id) ON DELETE CASCADE
);

CREATE TABLE stock_count_submission_remarks (
    submission_id UUID NOT NULL,
    remark_key VARCHAR(120) NOT NULL,
    remark_value VARCHAR(1000) NOT NULL,
    PRIMARY KEY (submission_id, remark_key),
    CONSTRAINT fk_stock_count_remarks_submission FOREIGN KEY (submission_id)
        REFERENCES stock_count_submissions (id) ON DELETE CASCADE
);

CREATE TABLE stock_receivings (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    tenant_id UUID NOT NULL,
    supplier_id UUID NOT NULL,
    received_by_user_id UUID NOT NULL,
    captured_at TIMESTAMPTZ NOT NULL,
    invoice_photo_name VARCHAR(500) NOT NULL,
    goods_photo_name VARCHAR(500) NOT NULL,
    review_status VARCHAR(24) NOT NULL DEFAULT 'Pending Review',
    reviewed_by_user_id UUID,
    reviewed_at TIMESTAMPTZ,
    review_note VARCHAR(1000) NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_stock_receivings_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id) ON DELETE RESTRICT,
    CONSTRAINT fk_stock_receivings_supplier_same_tenant FOREIGN KEY (tenant_id, supplier_id)
        REFERENCES stock_suppliers (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_stock_receivings_received_by FOREIGN KEY (tenant_id, received_by_user_id)
        REFERENCES users (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_stock_receivings_reviewed_by FOREIGN KEY (tenant_id, reviewed_by_user_id)
        REFERENCES users (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT uq_stock_receivings_tenant_id_id UNIQUE (tenant_id, id),
    CONSTRAINT ck_stock_receivings_review_status CHECK (review_status IN ('Pending Review', 'Approved', 'Rejected'))
);
CREATE INDEX ix_stock_receivings_tenant_captured_at ON stock_receivings (tenant_id, captured_at DESC);

CREATE TABLE stock_receiving_items (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    receiving_id UUID NOT NULL,
    sku_id UUID NOT NULL,
    position INTEGER NOT NULL,
    sku_name VARCHAR(120) NOT NULL,
    invoice_quantity NUMERIC(14,2) NOT NULL,
    received_quantity NUMERIC(14,2) NOT NULL,
    unit VARCHAR(32) NOT NULL,
    condition VARCHAR(80) NOT NULL,
    note VARCHAR(1000) NOT NULL DEFAULT '',
    CONSTRAINT fk_stock_receiving_items_receiving FOREIGN KEY (receiving_id)
        REFERENCES stock_receivings (id) ON DELETE CASCADE,
    CONSTRAINT fk_stock_receiving_items_sku FOREIGN KEY (sku_id)
        REFERENCES stock_skus (id) ON DELETE RESTRICT,
    CONSTRAINT uq_stock_receiving_items_position UNIQUE (receiving_id, position),
    CONSTRAINT ck_stock_receiving_items_position CHECK (position >= 0),
    CONSTRAINT ck_stock_receiving_items_quantities CHECK (invoice_quantity >= 0 AND received_quantity >= 0)
);

CREATE TABLE stock_audit_entries (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    tenant_id UUID NOT NULL,
    module VARCHAR(80) NOT NULL,
    action VARCHAR(120) NOT NULL,
    item_id UUID,
    item_name VARCHAR(160) NOT NULL,
    actor_name VARCHAR(120) NOT NULL,
    actor_employee_id VARCHAR(32) NOT NULL,
    actor_role VARCHAR(80) NOT NULL,
    captured_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    note VARCHAR(1000) NOT NULL DEFAULT '',
    CONSTRAINT fk_stock_audit_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id) ON DELETE RESTRICT,
    CONSTRAINT uq_stock_audit_tenant_id_id UNIQUE (tenant_id, id)
);
CREATE INDEX ix_stock_audit_tenant_captured_at ON stock_audit_entries (tenant_id, captured_at DESC);

CREATE TABLE stock_audit_entry_changes (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    stock_audit_entry_id UUID NOT NULL,
    position INTEGER NOT NULL,
    field_name VARCHAR(120) NOT NULL,
    old_value VARCHAR(1000) NOT NULL,
    new_value VARCHAR(1000) NOT NULL,
    CONSTRAINT fk_stock_audit_entry_changes_entry FOREIGN KEY (stock_audit_entry_id)
        REFERENCES stock_audit_entries (id) ON DELETE CASCADE,
    CONSTRAINT uq_stock_audit_entry_changes_position UNIQUE (stock_audit_entry_id, position),
    CONSTRAINT ck_stock_audit_entry_changes_position CHECK (position >= 0)
);

CREATE TABLE knowledge_sops (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    tenant_id UUID NOT NULL,
    tag_id UUID NOT NULL,
    youtube_url VARCHAR(500) NOT NULL,
    title VARCHAR(160) NOT NULL,
    expected_outcome VARCHAR(1000) NOT NULL,
    description TEXT NOT NULL,
    created_by_user_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_knowledge_sops_tenant FOREIGN KEY (tenant_id)
        REFERENCES tenants (id) ON DELETE RESTRICT,
    CONSTRAINT fk_knowledge_sops_tag_same_tenant FOREIGN KEY (tenant_id, tag_id)
        REFERENCES stock_tags (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_knowledge_sops_created_by FOREIGN KEY (tenant_id, created_by_user_id)
        REFERENCES users (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT uq_knowledge_sops_tenant_id_id UNIQUE (tenant_id, id),
    CONSTRAINT ck_knowledge_sops_youtube_url_not_blank CHECK (btrim(youtube_url) <> ''),
    CONSTRAINT ck_knowledge_sops_title_not_blank CHECK (btrim(title) <> ''),
    CONSTRAINT ck_knowledge_sops_outcome_not_blank CHECK (btrim(expected_outcome) <> ''),
    CONSTRAINT ck_knowledge_sops_description_not_blank CHECK (btrim(description) <> '')
);
CREATE INDEX ix_knowledge_sops_tenant_created_at
    ON knowledge_sops (tenant_id, created_at DESC);
CREATE INDEX ix_knowledge_sops_tenant_tag
    ON knowledge_sops (tenant_id, tag_id);

-- The first tenant, its default roles and the first Owner account are created
-- through the one-time Initial Setup API. Later tenants are created through
-- People -> Tenant, and later users through People -> User.


-- ============================================================================
-- User points
-- ============================================================================

-- Immutable tenant-scoped point adjustment ledger.

CREATE TABLE user_point_adjustments (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    tenant_id UUID NOT NULL,
    recipient_user_id UUID NOT NULL,
    adjusted_by_user_id UUID NOT NULL,
    points_delta INTEGER NOT NULL,
    reason VARCHAR(300) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_point_adjustments_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id) ON DELETE RESTRICT,
    CONSTRAINT fk_user_point_adjustments_recipient_same_tenant
        FOREIGN KEY (tenant_id, recipient_user_id)
        REFERENCES users (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_user_point_adjustments_actor_same_tenant
        FOREIGN KEY (tenant_id, adjusted_by_user_id)
        REFERENCES users (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT ck_user_point_adjustments_delta
        CHECK (points_delta BETWEEN -10 AND 10 AND points_delta <> 0),
    CONSTRAINT ck_user_point_adjustments_reason_not_blank
        CHECK (btrim(reason) <> '')
);

CREATE INDEX ix_user_point_adjustments_tenant_recipient_created
    ON user_point_adjustments (tenant_id, recipient_user_id, created_at DESC);
CREATE INDEX ix_user_point_adjustments_tenant_created
    ON user_point_adjustments (tenant_id, created_at DESC);


-- ============================================================================
-- Business reports
-- ============================================================================

-- Tenant-scoped business reporting schema.
-- Reports share one workflow header while type-specific tables keep each domain strict.

CREATE TABLE report_media (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    tenant_id UUID NOT NULL,
    storage_key VARCHAR(80) NOT NULL,
    content_type VARCHAR(40) NOT NULL,
    size_bytes BIGINT NOT NULL,
    content_bytes BYTEA NOT NULL,
    uploaded_by_user_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_report_media_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id) ON DELETE RESTRICT,
    CONSTRAINT fk_report_media_uploader_same_tenant
        FOREIGN KEY (tenant_id, uploaded_by_user_id)
        REFERENCES users (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT uq_report_media_tenant_id_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_report_media_tenant_key UNIQUE (tenant_id, storage_key),
    CONSTRAINT ck_report_media_size_positive CHECK (size_bytes > 0),
    CONSTRAINT ck_report_media_size_limit CHECK (size_bytes <= 5242880),
    CONSTRAINT ck_report_media_content_type
        CHECK (content_type IN ('image/jpeg', 'image/png'))
);
CREATE INDEX ix_report_media_tenant_created_at
    ON report_media (tenant_id, created_at DESC);

CREATE TABLE business_reports (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    tenant_id UUID NOT NULL,
    report_type VARCHAR(24) NOT NULL,
    report_date DATE NOT NULL,
    submitted_by_user_id UUID NOT NULL,
    workflow_status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    submitted_at TIMESTAMPTZ,
    reviewed_by_user_id UUID,
    reviewed_at TIMESTAMPTZ,
    review_note VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_business_reports_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id) ON DELETE RESTRICT,
    CONSTRAINT fk_business_reports_submitter_same_tenant
        FOREIGN KEY (tenant_id, submitted_by_user_id)
        REFERENCES users (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_business_reports_reviewer_same_tenant
        FOREIGN KEY (tenant_id, reviewed_by_user_id)
        REFERENCES users (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT uq_business_reports_tenant_id_id UNIQUE (tenant_id, id),
    CONSTRAINT ck_business_reports_type
        CHECK (report_type IN ('SALES', 'WASTE', 'DAILY_PHOTO', 'COMPLAINT')),
    CONSTRAINT ck_business_reports_workflow_status
        CHECK (workflow_status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED')),
    CONSTRAINT ck_business_reports_submission_time
        CHECK ((workflow_status = 'DRAFT' AND submitted_at IS NULL)
            OR (workflow_status <> 'DRAFT' AND submitted_at IS NOT NULL)),
    CONSTRAINT ck_business_reports_review_time
        CHECK ((workflow_status IN ('APPROVED', 'REJECTED') AND reviewed_at IS NOT NULL)
            OR (workflow_status NOT IN ('APPROVED', 'REJECTED') AND reviewed_at IS NULL)),
    CONSTRAINT ck_business_reports_review_actor
        CHECK ((reviewed_at IS NULL AND reviewed_by_user_id IS NULL)
            OR (reviewed_at IS NOT NULL AND reviewed_by_user_id IS NOT NULL))
);
CREATE UNIQUE INDEX uq_business_reports_sales_tenant_date
    ON business_reports (tenant_id, report_date)
    WHERE report_type = 'SALES';
CREATE UNIQUE INDEX uq_business_reports_daily_photo_user_date
    ON business_reports (tenant_id, submitted_by_user_id, report_date)
    WHERE report_type = 'DAILY_PHOTO';
CREATE INDEX ix_business_reports_tenant_type_date
    ON business_reports (tenant_id, report_type, report_date DESC);
CREATE INDEX ix_business_reports_tenant_workflow_date
    ON business_reports (tenant_id, workflow_status, report_date DESC);

CREATE TABLE sales_report_details (
    report_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    sales_rm NUMERIC(14, 2) NOT NULL DEFAULT 0,
    sub_total_rm NUMERIC(14, 2) NOT NULL DEFAULT 0,
    cash_received_by VARCHAR(120) NOT NULL,
    panda_sales_rm NUMERIC(14, 2) NOT NULL DEFAULT 0,
    ewallet_total_rm NUMERIC(14, 2) NOT NULL DEFAULT 0,
    staff_count INTEGER NOT NULL,
    CONSTRAINT fk_sales_report_details_report_same_tenant
        FOREIGN KEY (tenant_id, report_id)
        REFERENCES business_reports (tenant_id, id) ON DELETE CASCADE,
    CONSTRAINT ck_sales_report_details_amounts_non_negative
        CHECK (
            sales_rm >= 0
            AND sub_total_rm >= 0
            AND panda_sales_rm >= 0
            AND ewallet_total_rm >= 0
        ),
    CONSTRAINT ck_sales_report_details_total_reconciles
        CHECK (sales_rm = sub_total_rm + panda_sales_rm + ewallet_total_rm),
    CONSTRAINT ck_sales_report_details_cash_receiver_not_blank
        CHECK (btrim(cash_received_by) <> ''),
    CONSTRAINT ck_sales_report_details_staff_count_positive
        CHECK (staff_count BETWEEN 1 AND 500)
);
CREATE INDEX ix_sales_report_details_tenant_report
    ON sales_report_details (tenant_id, report_id);

CREATE TABLE sales_void_bills (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    tenant_id UUID NOT NULL,
    sales_report_id UUID NOT NULL,
    photo_media_id UUID NOT NULL,
    bill_number VARCHAR(80) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    amount_rm NUMERIC(14, 2) NOT NULL,
    created_by_user_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_sales_void_bills_report_same_tenant
        FOREIGN KEY (tenant_id, sales_report_id)
        REFERENCES business_reports (tenant_id, id) ON DELETE CASCADE,
    CONSTRAINT fk_sales_void_bills_media_same_tenant
        FOREIGN KEY (tenant_id, photo_media_id)
        REFERENCES report_media (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_sales_void_bills_creator_same_tenant
        FOREIGN KEY (tenant_id, created_by_user_id)
        REFERENCES users (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT ck_sales_void_bills_bill_number_not_blank CHECK (btrim(bill_number) <> ''),
    CONSTRAINT ck_sales_void_bills_reason_not_blank CHECK (btrim(reason) <> ''),
    CONSTRAINT ck_sales_void_bills_amount_positive CHECK (amount_rm > 0)
);
CREATE UNIQUE INDEX uq_sales_void_bills_report_bill_number_ci
    ON sales_void_bills (tenant_id, sales_report_id, lower(bill_number));
CREATE INDEX ix_sales_void_bills_tenant_report_created
    ON sales_void_bills (tenant_id, sales_report_id, created_at DESC);

CREATE TABLE waste_report_details (
    report_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    sku_id UUID,
    item_name VARCHAR(160) NOT NULL,
    quantity NUMERIC(14, 2) NOT NULL,
    unit VARCHAR(32) NOT NULL,
    estimated_unit_cost_rm NUMERIC(14, 2) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    photo_media_id UUID NOT NULL,
    CONSTRAINT fk_waste_report_details_report_same_tenant
        FOREIGN KEY (tenant_id, report_id)
        REFERENCES business_reports (tenant_id, id) ON DELETE CASCADE,
    CONSTRAINT fk_waste_report_details_sku_same_tenant
        FOREIGN KEY (tenant_id, sku_id)
        REFERENCES stock_skus (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_waste_report_details_media_same_tenant
        FOREIGN KEY (tenant_id, photo_media_id)
        REFERENCES report_media (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT ck_waste_report_details_item_not_blank CHECK (btrim(item_name) <> ''),
    CONSTRAINT ck_waste_report_details_unit_not_blank CHECK (btrim(unit) <> ''),
    CONSTRAINT ck_waste_report_details_reason_not_blank CHECK (btrim(reason) <> ''),
    CONSTRAINT ck_waste_report_details_quantity_positive CHECK (quantity > 0),
    CONSTRAINT ck_waste_report_details_cost_non_negative CHECK (estimated_unit_cost_rm >= 0)
);
CREATE INDEX ix_waste_report_details_tenant_sku
    ON waste_report_details (tenant_id, sku_id);

CREATE TABLE daily_report_photos (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    tenant_id UUID NOT NULL,
    report_id UUID NOT NULL,
    photo_media_id UUID NOT NULL,
    created_by_user_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_daily_report_photos_report_same_tenant
        FOREIGN KEY (tenant_id, report_id)
        REFERENCES business_reports (tenant_id, id) ON DELETE CASCADE,
    CONSTRAINT fk_daily_report_photos_media_same_tenant
        FOREIGN KEY (tenant_id, photo_media_id)
        REFERENCES report_media (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_daily_report_photos_creator_same_tenant
        FOREIGN KEY (tenant_id, created_by_user_id)
        REFERENCES users (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT uq_daily_report_photos_report_media
        UNIQUE (tenant_id, report_id, photo_media_id)
);
CREATE INDEX ix_daily_report_photos_tenant_report_created
    ON daily_report_photos (tenant_id, report_id, created_at);

CREATE TABLE complaint_report_details (
    report_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    photo_media_id UUID NOT NULL,
    customer_gender VARCHAR(16) NOT NULL,
    estimated_age INTEGER NOT NULL,
    complaint_info VARCHAR(1500) NOT NULL,
    phone_e164 VARCHAR(32),
    action_taken VARCHAR(1500) NOT NULL,
    compensation_amount_rm NUMERIC(14, 2),
    complaint_status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    resolved_at TIMESTAMPTZ,
    CONSTRAINT fk_complaint_report_details_report_same_tenant
        FOREIGN KEY (tenant_id, report_id)
        REFERENCES business_reports (tenant_id, id) ON DELETE CASCADE,
    CONSTRAINT fk_complaint_report_details_media_same_tenant
        FOREIGN KEY (tenant_id, photo_media_id)
        REFERENCES report_media (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT ck_complaint_report_details_gender
        CHECK (customer_gender IN ('MALE', 'FEMALE', 'OTHER', 'UNKNOWN')),
    CONSTRAINT ck_complaint_report_details_age
        CHECK (estimated_age BETWEEN 1 AND 120),
    CONSTRAINT ck_complaint_report_details_info_not_blank CHECK (btrim(complaint_info) <> ''),
    CONSTRAINT ck_complaint_report_details_action_not_blank CHECK (btrim(action_taken) <> ''),
    CONSTRAINT ck_complaint_report_details_compensation_non_negative
        CHECK (compensation_amount_rm IS NULL OR compensation_amount_rm >= 0),
    CONSTRAINT ck_complaint_report_details_status
        CHECK (complaint_status IN ('OPEN', 'RESOLVED')),
    CONSTRAINT ck_complaint_report_details_resolved_time
        CHECK ((complaint_status = 'RESOLVED' AND resolved_at IS NOT NULL)
            OR (complaint_status = 'OPEN' AND resolved_at IS NULL))
);
CREATE INDEX ix_complaint_report_details_tenant_status
    ON complaint_report_details (tenant_id, complaint_status);


-- ============================================================================
-- Failed attendance face-attempt evidence
-- ============================================================================

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
