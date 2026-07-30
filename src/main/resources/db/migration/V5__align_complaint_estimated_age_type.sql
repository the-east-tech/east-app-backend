ALTER TABLE complaint_report_details
    ALTER COLUMN estimated_age TYPE INTEGER
    USING estimated_age::INTEGER;
