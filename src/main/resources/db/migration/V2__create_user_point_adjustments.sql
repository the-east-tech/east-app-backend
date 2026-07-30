-- EastApp data is retained from this migration onward.
-- Point totals are derived from this immutable tenant-scoped adjustment ledger.

CREATE TABLE user_point_adjustments (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    tenant_id UUID NOT NULL,
    recipient_user_id UUID NOT NULL,
    adjusted_by_user_id UUID NOT NULL,
    points_delta SMALLINT NOT NULL,
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
