-- PENDING previously meant a completed rejection/return in Stock.
-- Keep existing history, but give that state the same name in storage and API.
DROP INDEX uq_stock_counts_tenant_sku_cycle_active;

ALTER TABLE stock_sku_change_requests
    DROP CONSTRAINT ck_stock_sku_changes_status;

UPDATE stock_sku_change_requests
SET workflow_status = 'REJECTED'
WHERE workflow_status = 'PENDING';

UPDATE stock_count_submissions
SET review_status = 'REJECTED'
WHERE review_status = 'PENDING';

UPDATE stock_receivables
SET review_status = 'REJECTED'
WHERE review_status = 'PENDING';

ALTER TABLE stock_sku_change_requests
    ADD CONSTRAINT ck_stock_sku_changes_status
    CHECK (workflow_status IN ('REJECTED', 'SUBMITTED', 'DONE'));

CREATE UNIQUE INDEX uq_stock_counts_tenant_sku_cycle_active
    ON stock_count_submissions (tenant_id, sku_id, count_cycle_started_at)
    WHERE review_status <> 'REJECTED';
