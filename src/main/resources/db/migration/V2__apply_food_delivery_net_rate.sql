-- Food-delivery platforms retain an estimated 40 percent commission.
-- Keep the cashier-entered gross amount, but reconcile recognised Total Sales
-- using the estimated 60 percent net proceeds.

ALTER TABLE sales_report_details
    DROP CONSTRAINT ck_sales_report_details_total_reconciles;

UPDATE sales_report_details
SET sales_rm = round(sub_total_rm + (panda_sales_rm * 0.60) + ewallet_total_rm, 2);

ALTER TABLE sales_report_details
    ADD CONSTRAINT ck_sales_report_details_total_reconciles
    CHECK (sales_rm = round(sub_total_rm + (panda_sales_rm * 0.60) + ewallet_total_rm, 2));
