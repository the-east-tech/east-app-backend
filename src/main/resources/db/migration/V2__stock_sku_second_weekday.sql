ALTER TABLE stock_skus ADD COLUMN stock_check_day_2 INTEGER;

ALTER TABLE stock_skus ADD CONSTRAINT ck_stock_skus_second_weekday
    CHECK (stock_check_day_2 IS NULL OR (
        stock_check_schedule = 'WEEKLY'
        AND stock_check_day_2 BETWEEN 1 AND 7
        AND stock_check_day_2 <> stock_check_day
    ));

-- Existing submitted records changed live balance under the previous workflow.
-- Preserve their review and return behaviour; new submissions use FALSE.
ALTER TABLE stock_count_submissions
    ADD COLUMN balance_applied_at_submission BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE stock_count_submissions
    ALTER COLUMN balance_applied_at_submission SET DEFAULT FALSE;

ALTER TABLE stock_receivables
    ADD COLUMN balance_applied_at_submission BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE stock_receivables
    ALTER COLUMN balance_applied_at_submission SET DEFAULT FALSE;
