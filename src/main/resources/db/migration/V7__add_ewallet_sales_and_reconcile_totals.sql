-- Keep existing data and add the missing payment channel.
ALTER TABLE sales_report_details
    ADD COLUMN ewallet_total_rm NUMERIC(14, 2) NOT NULL DEFAULT 0;

-- sales_rm is now the server-derived Total Sales value.
UPDATE sales_report_details
SET sales_rm = sub_total_rm + panda_sales_rm + ewallet_total_rm;

ALTER TABLE sales_report_details
    DROP CONSTRAINT ck_sales_report_details_amounts_non_negative;
ALTER TABLE sales_report_details
    ADD CONSTRAINT ck_sales_report_details_amounts_non_negative
        CHECK (
            sales_rm >= 0
            AND sub_total_rm >= 0
            AND panda_sales_rm >= 0
            AND ewallet_total_rm >= 0
        ),
    ADD CONSTRAINT ck_sales_report_details_total_reconciles
        CHECK (sales_rm = sub_total_rm + panda_sales_rm + ewallet_total_rm);
