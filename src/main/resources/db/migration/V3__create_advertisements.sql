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
        REFERENCES users (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT ck_advertisement_period CHECK (ends_at > starts_at),
    CONSTRAINT ck_advertisement_order CHECK (display_order BETWEEN 0 AND 3)
);

CREATE INDEX ix_advertisements_tenant_schedule
    ON advertisements (tenant_id, active, starts_at, ends_at);
