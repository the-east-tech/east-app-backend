-- EastApp development baseline.
-- Until production release, the development database is disposable and this
-- single V1 file is the source of truth for the complete schema.

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
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_login_identities_password_hash_not_blank CHECK (btrim(password_hash) <> '')
);

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    tenant_id UUID NOT NULL,
    identity_id UUID NOT NULL,
    employee_id VARCHAR(32) NOT NULL,
    full_name VARCHAR(120) NOT NULL,
    phone_e164 VARCHAR(16) NOT NULL,
    profile_photo_key VARCHAR(255),
    birth_date DATE,
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
    CONSTRAINT ck_users_full_name_not_blank CHECK (btrim(full_name) <> ''),
    CONSTRAINT ck_users_phone_e164_format CHECK (phone_e164 ~ '^\+[1-9][0-9]{7,14}$'),
    CONSTRAINT ck_users_profile_photo_key_not_blank CHECK (profile_photo_key IS NULL OR btrim(profile_photo_key) <> ''),
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

CREATE TABLE stock_audit_changes (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    audit_entry_id UUID NOT NULL,
    position INTEGER NOT NULL,
    field_name VARCHAR(120) NOT NULL,
    old_value VARCHAR(1000) NOT NULL,
    new_value VARCHAR(1000) NOT NULL,
    CONSTRAINT fk_stock_audit_changes_entry FOREIGN KEY (audit_entry_id)
        REFERENCES stock_audit_entries (id) ON DELETE CASCADE,
    CONSTRAINT uq_stock_audit_changes_position UNIQUE (audit_entry_id, position),
    CONSTRAINT ck_stock_audit_changes_position CHECK (position >= 0)
);

-- The first tenant, its default roles and the first Owner account are created
-- through the one-time Initial Setup API. Later tenants are created through
-- People -> Tenant, and later users through People -> User.
