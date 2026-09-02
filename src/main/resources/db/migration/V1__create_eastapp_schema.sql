-- EastApp clean reset-per-release schema (v106).
-- This V1 contains the complete schema for a brand-new EastApp database.
-- While the reset-per-release policy is active, merge every schema change into
-- this file, keep V1 as the only migration, and reset the database each release.
-- PostgreSQL owns only structural integrity: keys, relationships and essential
-- uniqueness. Business validation remains in the Java application.

CREATE TABLE application_setup (
    id SMALLINT PRIMARY KEY,
    setup_code VARCHAR(10),
    setup_code_expires_at TIMESTAMPTZ
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
    CONSTRAINT uq_tenants_employee_id_prefix UNIQUE (employee_id_prefix)
);

CREATE TABLE roles (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    tenant_id UUID NOT NULL,
    system_key VARCHAR(32) NOT NULL,
    name VARCHAR(80) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_roles_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id) ON DELETE RESTRICT,
    CONSTRAINT uq_roles_tenant_id_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_roles_tenant_system_key UNIQUE (tenant_id, system_key)
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
    CONSTRAINT uq_login_identities_phone_e164 UNIQUE (phone_e164)
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
    CONSTRAINT uq_users_identity_tenant UNIQUE (identity_id, tenant_id)
);
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
    CONSTRAINT uq_user_sessions_token_hash UNIQUE (token_hash)
);
CREATE INDEX ix_user_sessions_identity_id ON user_sessions (identity_id);

-- One immutable business event drives both Home activity and notifications.
-- Recipient rows only hold read/dismiss state, avoiding duplicate event data.
CREATE TABLE activity_events (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    tenant_id UUID NOT NULL,
    actor_user_id UUID NOT NULL,
    actor_name VARCHAR(120) NOT NULL,
    actor_employee_id VARCHAR(32) NOT NULL,
    actor_role VARCHAR(80) NOT NULL,
    module VARCHAR(64) NOT NULL,
    action VARCHAR(64) NOT NULL,
    entity_type VARCHAR(100) NOT NULL,
    subject VARCHAR(240) NOT NULL DEFAULT '',
    detail VARCHAR(2000) NOT NULL DEFAULT '',
    target_id UUID,
    route VARCHAR(240) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_activity_events_tenant FOREIGN KEY (tenant_id)
        REFERENCES tenants (id) ON DELETE RESTRICT,
    CONSTRAINT fk_activity_events_actor_same_tenant FOREIGN KEY (tenant_id, actor_user_id)
        REFERENCES users (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT uq_activity_events_tenant_id_id UNIQUE (tenant_id, id)
);
CREATE INDEX ix_activity_events_tenant_time
    ON activity_events (tenant_id, occurred_at DESC, id DESC);

CREATE TABLE user_notifications (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    tenant_id UUID NOT NULL,
    recipient_user_id UUID NOT NULL,
    activity_event_id UUID NOT NULL,
    read_at TIMESTAMPTZ,
    dismissed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_notifications_tenant FOREIGN KEY (tenant_id)
        REFERENCES tenants (id) ON DELETE RESTRICT,
    CONSTRAINT fk_user_notifications_recipient_same_tenant FOREIGN KEY (tenant_id, recipient_user_id)
        REFERENCES users (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_user_notifications_event_same_tenant FOREIGN KEY (tenant_id, activity_event_id)
        REFERENCES activity_events (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT uq_user_notifications_event_recipient UNIQUE (activity_event_id, recipient_user_id)
);
CREATE INDEX ix_user_notifications_inbox
    ON user_notifications (tenant_id, recipient_user_id, created_at DESC, id DESC)
    WHERE dismissed_at IS NULL;
CREATE INDEX ix_user_notifications_unread
    ON user_notifications (tenant_id, recipient_user_id)
    WHERE read_at IS NULL AND dismissed_at IS NULL;

CREATE TABLE push_devices (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    tenant_id UUID NOT NULL,
    user_id UUID NOT NULL,
    session_id UUID NOT NULL,
    token VARCHAR(2048) NOT NULL,
    platform VARCHAR(16) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    last_seen_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_push_devices_user_same_tenant FOREIGN KEY (tenant_id, user_id)
        REFERENCES users (tenant_id, id) ON DELETE CASCADE,
    CONSTRAINT fk_push_devices_session FOREIGN KEY (session_id)
        REFERENCES user_sessions (id) ON DELETE CASCADE,
    CONSTRAINT uq_push_devices_token UNIQUE (token)
);
CREATE INDEX ix_push_devices_user_active
    ON push_devices (tenant_id, user_id, active);

CREATE TABLE push_outbox (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    notification_id UUID NOT NULL,
    device_id UUID NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    sent_at TIMESTAMPTZ,
    last_error VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_push_outbox_notification FOREIGN KEY (notification_id)
        REFERENCES user_notifications (id) ON DELETE CASCADE,
    CONSTRAINT fk_push_outbox_device FOREIGN KEY (device_id)
        REFERENCES push_devices (id) ON DELETE CASCADE,
    CONSTRAINT uq_push_outbox_notification_device UNIQUE (notification_id, device_id)
);
CREATE INDEX ix_push_outbox_due
    ON push_outbox (next_attempt_at, created_at, id)
    WHERE sent_at IS NULL;

CREATE TABLE attendance_qr_codes (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    tenant_id UUID NOT NULL,
    generated_by_user_id UUID NOT NULL,
    event_type VARCHAR(16) NOT NULL,
    secret_hash BYTEA NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_attendance_qr_codes_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id) ON DELETE RESTRICT,
    CONSTRAINT fk_attendance_qr_codes_generator_same_tenant FOREIGN KEY (tenant_id, generated_by_user_id)
        REFERENCES users (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT uq_attendance_qr_codes_tenant_id_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_attendance_qr_codes_secret_hash UNIQUE (secret_hash)
);
CREATE INDEX ix_attendance_qr_codes_active_fifo
    ON attendance_qr_codes (tenant_id, event_type, created_at ASC, id ASC)
    WHERE revoked_at IS NULL;
CREATE INDEX ix_attendance_qr_codes_expiry ON attendance_qr_codes (expires_at);

CREATE TABLE attendance_events (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    tenant_id UUID NOT NULL,
    user_id UUID NOT NULL,
    user_session_id UUID,
    qr_code_id UUID NOT NULL,
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
    CONSTRAINT fk_attendance_events_qr_code_same_tenant FOREIGN KEY (tenant_id, qr_code_id)
        REFERENCES attendance_qr_codes (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT uq_attendance_events_tenant_client_event UNIQUE (tenant_id, client_event_id)
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
    CONSTRAINT uq_stock_media_tenant_key UNIQUE (tenant_id, storage_key)
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
    CONSTRAINT uq_stock_tags_tenant_id_id UNIQUE (tenant_id, id)
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
    CONSTRAINT uq_stock_suppliers_tenant_id_id UNIQUE (tenant_id, id)
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
    CONSTRAINT uq_stock_skus_tenant_id_id UNIQUE (tenant_id, id)
);
CREATE INDEX ix_stock_skus_tenant_tag1 ON stock_skus (tenant_id, tag1_id);
CREATE INDEX ix_stock_skus_tenant_tag2 ON stock_skus (tenant_id, tag2_id);

CREATE TABLE stock_sku_suppliers (
    sku_id UUID NOT NULL,
    supplier_id UUID NOT NULL,
    PRIMARY KEY (sku_id, supplier_id),
    CONSTRAINT fk_stock_sku_suppliers_sku FOREIGN KEY (sku_id)
        REFERENCES stock_skus (id) ON DELETE CASCADE,
    CONSTRAINT fk_stock_sku_suppliers_supplier FOREIGN KEY (supplier_id)
        REFERENCES stock_suppliers (id) ON DELETE RESTRICT
);

CREATE TABLE stock_sku_assignees (
    sku_id UUID NOT NULL,
    position INTEGER NOT NULL,
    assigned_staff_name VARCHAR(120) NOT NULL,
    PRIMARY KEY (sku_id, position),
    CONSTRAINT fk_stock_sku_assignees_sku FOREIGN KEY (sku_id) REFERENCES stock_skus (id) ON DELETE CASCADE
);

CREATE TABLE stock_sku_receiving_checklist (
    sku_id UUID NOT NULL,
    position INTEGER NOT NULL,
    checklist_item VARCHAR(300) NOT NULL,
    PRIMARY KEY (sku_id, position),
    CONSTRAINT fk_stock_sku_checklist_sku FOREIGN KEY (sku_id) REFERENCES stock_skus (id) ON DELETE CASCADE
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
    CONSTRAINT uq_stock_counts_tenant_sku_cycle UNIQUE (tenant_id, sku_id, count_cycle_started_at)
);
CREATE INDEX ix_stock_counts_tenant_captured_at ON stock_count_submissions (tenant_id, captured_at DESC);
CREATE INDEX ix_stock_counts_tenant_review_captured_at ON stock_count_submissions (tenant_id, review_status, captured_at DESC);

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
    CONSTRAINT uq_stock_receivings_tenant_id_id UNIQUE (tenant_id, id)
);
CREATE INDEX ix_stock_receivings_tenant_captured_at ON stock_receivings (tenant_id, captured_at DESC);
CREATE INDEX ix_stock_receivings_tenant_review_captured_at ON stock_receivings (tenant_id, review_status, captured_at DESC);

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
    CONSTRAINT uq_stock_receiving_items_position UNIQUE (receiving_id, position)
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
    CONSTRAINT uq_stock_audit_entry_changes_position UNIQUE (stock_audit_entry_id, position)
);

CREATE TABLE knowledge_sops (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    tenant_id UUID NOT NULL,
    language VARCHAR(20) NOT NULL,
    link_group_id UUID NOT NULL,
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
    CONSTRAINT uq_knowledge_sops_group_language
        UNIQUE (tenant_id, link_group_id, language)
);
CREATE INDEX ix_knowledge_sops_tenant_created_at
    ON knowledge_sops (tenant_id, created_at DESC);
CREATE INDEX ix_knowledge_sops_tenant_tag
    ON knowledge_sops (tenant_id, tag_id);
CREATE TABLE knowledge_sop_watch_sessions (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    user_id UUID NOT NULL,
    sop_id UUID NOT NULL,
    played_seconds BIGINT NOT NULL DEFAULT 0,
    started_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_heartbeat_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_sop_watch_session_user_same_tenant
        FOREIGN KEY (tenant_id, user_id)
        REFERENCES users (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_sop_watch_session_sop_same_tenant
        FOREIGN KEY (tenant_id, sop_id)
        REFERENCES knowledge_sops (tenant_id, id) ON DELETE CASCADE,
    CONSTRAINT uq_sop_watch_session_tenant_id_id UNIQUE (tenant_id, id)
);
CREATE INDEX ix_sop_watch_session_tenant_user_time
    ON knowledge_sop_watch_sessions (tenant_id, user_id, last_heartbeat_at DESC);
CREATE INDEX ix_sop_watch_session_tenant_sop
    ON knowledge_sop_watch_sessions (tenant_id, sop_id);

CREATE TABLE translation_cache (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    tenant_id UUID NOT NULL,
    source_language VARCHAR(16) NOT NULL,
    target_language VARCHAR(16) NOT NULL,
    source_hash VARCHAR(64) NOT NULL,
    source_text TEXT NOT NULL,
    translated_text TEXT NOT NULL,
    provider VARCHAR(40) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_translation_cache_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id) ON DELETE CASCADE,
    CONSTRAINT uq_translation_cache_lookup
        UNIQUE (tenant_id, source_language, target_language, source_hash)
);

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
        REFERENCES users (tenant_id, id) ON DELETE RESTRICT
);

CREATE INDEX ix_user_point_adjustments_tenant_recipient_created
    ON user_point_adjustments (tenant_id, recipient_user_id, created_at DESC);
CREATE INDEX ix_user_point_adjustments_tenant_created
    ON user_point_adjustments (tenant_id, created_at DESC);


-- ============================================================================
-- Business reports
-- ============================================================================

-- Tenant-scoped business reporting schema.
-- Reports share one workflow header while type-specific data remains separate.

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
    CONSTRAINT uq_report_media_tenant_key UNIQUE (tenant_id, storage_key)
);
CREATE INDEX ix_report_media_tenant_created_at
    ON report_media (tenant_id, created_at DESC);

-- Tenant-scoped Home advertisements. Images reuse report_media storage.
CREATE TABLE advertisements (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    tenant_id UUID NOT NULL,
    image_storage_key VARCHAR(80) NOT NULL,
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    display_order INTEGER NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by_user_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_advertisements_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id) ON DELETE CASCADE,
    CONSTRAINT fk_advertisements_media_same_tenant
        FOREIGN KEY (tenant_id, image_storage_key)
        REFERENCES report_media (tenant_id, storage_key) ON DELETE RESTRICT,
    CONSTRAINT fk_advertisements_creator_same_tenant
        FOREIGN KEY (tenant_id, created_by_user_id)
        REFERENCES users (tenant_id, id) ON DELETE RESTRICT
);

CREATE INDEX ix_advertisements_tenant_schedule
    ON advertisements (tenant_id, active, starts_at, ends_at);

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
    CONSTRAINT uq_business_reports_tenant_id_id UNIQUE (tenant_id, id)
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
    cash_received_by_user_id UUID NOT NULL,
    cash_received_by VARCHAR(120) NOT NULL,
    panda_sales_rm NUMERIC(14, 2) NOT NULL DEFAULT 0,
    ewallet_total_rm NUMERIC(14, 2) NOT NULL DEFAULT 0,
    staff_count INTEGER NOT NULL,
    CONSTRAINT fk_sales_report_details_report_same_tenant
        FOREIGN KEY (tenant_id, report_id)
        REFERENCES business_reports (tenant_id, id) ON DELETE CASCADE,
    CONSTRAINT fk_sales_report_details_cash_receiver_same_tenant
        FOREIGN KEY (tenant_id, cash_received_by_user_id)
        REFERENCES users (tenant_id, id) ON DELETE RESTRICT
);
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
        REFERENCES users (tenant_id, id) ON DELETE RESTRICT
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
        REFERENCES report_media (tenant_id, id) ON DELETE RESTRICT
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
        REFERENCES report_media (tenant_id, id) ON DELETE RESTRICT
);
CREATE INDEX ix_complaint_report_details_tenant_status
    ON complaint_report_details (tenant_id, complaint_status);

-- Tasks
CREATE TABLE stock_tag_assignees (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    tenant_id UUID NOT NULL,
    tag_id UUID NOT NULL,
    user_id UUID NOT NULL,
    assigned_by_user_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_stock_tag_assignees_tag_same_tenant
        FOREIGN KEY (tenant_id, tag_id)
        REFERENCES stock_tags (tenant_id, id) ON DELETE CASCADE,
    CONSTRAINT fk_stock_tag_assignees_user_same_tenant
        FOREIGN KEY (tenant_id, user_id)
        REFERENCES users (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_stock_tag_assignees_actor_same_tenant
        FOREIGN KEY (tenant_id, assigned_by_user_id)
        REFERENCES users (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT uq_stock_tag_assignees_tenant_tag_user
        UNIQUE (tenant_id, tag_id, user_id)
);
CREATE INDEX ix_stock_tag_assignees_tenant_user
    ON stock_tag_assignees (tenant_id, user_id, tag_id);

-- A template belongs to one Stock Tag. Each business date has one shared task
-- record, regardless of how many users are assigned to that Tag.
CREATE TABLE task_templates (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    tenant_id UUID NOT NULL,
    tag_id UUID NOT NULL,
    linked_sop_id UUID,
    title VARCHAR(160) NOT NULL,
    instruction VARCHAR(1000) NOT NULL DEFAULT '',
    required_photo_count INTEGER NOT NULL,
    schedule_type VARCHAR(16) NOT NULL,
    first_task_date DATE NOT NULL,
    end_date DATE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by_user_id UUID NOT NULL,
    updated_by_user_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_task_templates_tag_same_tenant
        FOREIGN KEY (tenant_id, tag_id)
        REFERENCES stock_tags (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_task_templates_sop_same_tenant
        FOREIGN KEY (tenant_id, linked_sop_id)
        REFERENCES knowledge_sops (tenant_id, id)
        ON DELETE SET NULL (linked_sop_id),
    CONSTRAINT fk_task_templates_creator_same_tenant
        FOREIGN KEY (tenant_id, created_by_user_id)
        REFERENCES users (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_task_templates_updater_same_tenant
        FOREIGN KEY (tenant_id, updated_by_user_id)
        REFERENCES users (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT uq_task_templates_tenant_id_id UNIQUE (tenant_id, id)
);
CREATE INDEX ix_task_templates_tenant_active_tag
    ON task_templates (tenant_id, active, first_task_date, end_date, tag_id, lower(title));
CREATE INDEX ix_task_templates_tenant_linked_sop
    ON task_templates (tenant_id, linked_sop_id)
    WHERE linked_sop_id IS NOT NULL;

CREATE TABLE task_template_checklist_items (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    tenant_id UUID NOT NULL,
    template_id UUID NOT NULL,
    position INTEGER NOT NULL,
    description VARCHAR(300) NOT NULL,
    CONSTRAINT fk_task_template_checks_template_same_tenant
        FOREIGN KEY (tenant_id, template_id)
        REFERENCES task_templates (tenant_id, id) ON DELETE CASCADE,
    CONSTRAINT uq_task_template_checks_position
        UNIQUE (tenant_id, template_id, position)
);

CREATE TABLE task_records (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    tenant_id UUID NOT NULL,
    template_id UUID NOT NULL,
    tag_id UUID NOT NULL,
    task_date DATE NOT NULL,
    title VARCHAR(160) NOT NULL,
    instruction VARCHAR(1000) NOT NULL DEFAULT '',
    linked_sop_id UUID,
    tag_name VARCHAR(80) NOT NULL,
    required_photo_count INTEGER NOT NULL,
    schedule_type VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    submitted_by_user_id UUID,
    submitted_by_role VARCHAR(32),
    submitted_at TIMESTAMPTZ,
    rating INTEGER,
    rating_comment VARCHAR(1000),
    rated_by_user_id UUID,
    rated_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_task_records_template_same_tenant
        FOREIGN KEY (tenant_id, template_id)
        REFERENCES task_templates (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_task_records_tag_same_tenant
        FOREIGN KEY (tenant_id, tag_id)
        REFERENCES stock_tags (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_task_records_sop_same_tenant
        FOREIGN KEY (tenant_id, linked_sop_id)
        REFERENCES knowledge_sops (tenant_id, id)
        ON DELETE SET NULL (linked_sop_id),
    CONSTRAINT fk_task_records_submitter_same_tenant
        FOREIGN KEY (tenant_id, submitted_by_user_id)
        REFERENCES users (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_task_records_rater_same_tenant
        FOREIGN KEY (tenant_id, rated_by_user_id)
        REFERENCES users (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT uq_task_records_tenant_id_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_task_records_template_date UNIQUE (tenant_id, template_id, task_date)
);
CREATE INDEX ix_task_records_tenant_date_status
    ON task_records (tenant_id, task_date DESC, status, tag_id);
CREATE INDEX ix_task_records_tenant_submitter_date
    ON task_records (tenant_id, submitted_by_user_id, task_date DESC);
CREATE INDEX ix_task_records_tenant_linked_sop
    ON task_records (tenant_id, linked_sop_id)
    WHERE linked_sop_id IS NOT NULL;

CREATE TABLE task_record_checklist_items (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    tenant_id UUID NOT NULL,
    record_id UUID NOT NULL,
    position INTEGER NOT NULL,
    description VARCHAR(300) NOT NULL,
    completed_by_user_id UUID,
    completed_at TIMESTAMPTZ,
    CONSTRAINT fk_task_record_checks_record_same_tenant
        FOREIGN KEY (tenant_id, record_id)
        REFERENCES task_records (tenant_id, id) ON DELETE CASCADE,
    CONSTRAINT fk_task_record_checks_user_same_tenant
        FOREIGN KEY (tenant_id, completed_by_user_id)
        REFERENCES users (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT uq_task_record_checks_position UNIQUE (tenant_id, record_id, position)
);

CREATE TABLE task_photos (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    tenant_id UUID NOT NULL,
    record_id UUID NOT NULL,
    photo_media_id UUID NOT NULL,
    submitted_by_user_id UUID NOT NULL,
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_task_photos_record_same_tenant
        FOREIGN KEY (tenant_id, record_id)
        REFERENCES task_records (tenant_id, id) ON DELETE CASCADE,
    CONSTRAINT fk_task_photos_media_same_tenant
        FOREIGN KEY (tenant_id, photo_media_id)
        REFERENCES report_media (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_task_photos_submitter_same_tenant
        FOREIGN KEY (tenant_id, submitted_by_user_id)
        REFERENCES users (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT uq_task_photos_media UNIQUE (tenant_id, photo_media_id)
);
CREATE INDEX ix_task_photos_tenant_record_time
    ON task_photos (tenant_id, record_id, submitted_at, id);

CREATE TABLE task_audit_entries (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    tenant_id UUID NOT NULL,
    template_id UUID,
    record_id UUID,
    actor_user_id UUID NOT NULL,
    action VARCHAR(48) NOT NULL,
    details VARCHAR(1200) NOT NULL DEFAULT '',
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_task_audit_template_same_tenant
        FOREIGN KEY (tenant_id, template_id)
        REFERENCES task_templates (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_task_audit_record_same_tenant
        FOREIGN KEY (tenant_id, record_id)
        REFERENCES task_records (tenant_id, id) ON DELETE CASCADE,
    CONSTRAINT fk_task_audit_actor_same_tenant
        FOREIGN KEY (tenant_id, actor_user_id)
        REFERENCES users (tenant_id, id) ON DELETE RESTRICT
);
CREATE INDEX ix_task_audit_tenant_record_time
    ON task_audit_entries (tenant_id, record_id, occurred_at, id);
CREATE INDEX ix_task_audit_tenant_template_time
    ON task_audit_entries (tenant_id, template_id, occurred_at, id)
    WHERE record_id IS NULL;
