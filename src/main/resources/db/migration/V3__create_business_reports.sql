-- Business reporting starts here. Existing data and earlier migrations are preserved.
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
    staff_count SMALLINT NOT NULL,
    CONSTRAINT fk_sales_report_details_report_same_tenant
        FOREIGN KEY (tenant_id, report_id)
        REFERENCES business_reports (tenant_id, id) ON DELETE CASCADE,
    CONSTRAINT ck_sales_report_details_amounts_non_negative
        CHECK (sales_rm >= 0 AND sub_total_rm >= 0 AND panda_sales_rm >= 0),
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
    estimated_age SMALLINT NOT NULL,
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
