-- Unify approval persistence to PENDING / SUBMITTED / DONE and add the
-- minimal supplier order marker used to gate Receiving.

ALTER TABLE stock_suppliers
    ADD COLUMN purchase_message_template VARCHAR(2000) NOT NULL
        DEFAULT E'Hi, please prepare the following items:\n\n{items}\n\n{date}\nPlease confirm availability and delivery time. Thank u.',
    ADD COLUMN order_state VARCHAR(24) NOT NULL DEFAULT 'NONE',
    ADD COLUMN current_order_reference UUID,
    ADD COLUMN ordered_at TIMESTAMPTZ,
    ADD COLUMN ordered_by_user_id UUID,
    ADD COLUMN ordered_message VARCHAR(4000) NOT NULL DEFAULT '';

ALTER TABLE stock_suppliers
    ADD CONSTRAINT fk_stock_suppliers_ordered_by_same_tenant
        FOREIGN KEY (tenant_id, ordered_by_user_id)
        REFERENCES users (tenant_id, id) ON DELETE RESTRICT;

CREATE INDEX ix_stock_suppliers_tenant_order_state
    ON stock_suppliers (tenant_id, order_state, supplier_name);

UPDATE stock_count_submissions
SET review_status = CASE review_status
    WHEN 'Approved' THEN 'DONE'
    WHEN 'Rejected' THEN 'PENDING'
    WHEN 'Pending Review' THEN 'SUBMITTED'
    WHEN 'Pending' THEN 'SUBMITTED'
    ELSE review_status
END;

ALTER TABLE stock_count_submissions
    ALTER COLUMN review_status SET DEFAULT 'SUBMITTED';

ALTER TABLE stock_count_submissions
    DROP CONSTRAINT uq_stock_counts_tenant_sku_cycle;

CREATE UNIQUE INDEX uq_stock_counts_tenant_sku_cycle_active
    ON stock_count_submissions (tenant_id, sku_id, count_cycle_started_at)
    WHERE review_status <> 'PENDING';

UPDATE stock_receivings
SET review_status = CASE review_status
    WHEN 'Approved' THEN 'DONE'
    WHEN 'Rejected' THEN 'PENDING'
    WHEN 'Pending Review' THEN 'SUBMITTED'
    WHEN 'Pending' THEN 'SUBMITTED'
    ELSE review_status
END;

ALTER TABLE stock_receivings
    ALTER COLUMN review_status SET DEFAULT 'SUBMITTED',
    ADD COLUMN order_reference UUID;

CREATE INDEX ix_stock_receivings_tenant_order_reference
    ON stock_receivings (tenant_id, order_reference, captured_at DESC)
    WHERE order_reference IS NOT NULL;

CREATE UNIQUE INDEX uq_stock_receivings_active_order_reference
    ON stock_receivings (tenant_id, order_reference)
    WHERE order_reference IS NOT NULL AND review_status <> 'PENDING';

UPDATE business_reports
SET workflow_status = CASE workflow_status
    WHEN 'APPROVED' THEN 'DONE'
    WHEN 'REJECTED' THEN 'PENDING'
    WHEN 'DRAFT' THEN 'PENDING'
    ELSE workflow_status
END;

ALTER TABLE business_reports
    ALTER COLUMN workflow_status SET DEFAULT 'PENDING';
