-- Align PostgreSQL column types with Java int mappings used by Hibernate.
-- This migration is non-destructive: SMALLINT values are widened to INTEGER.

ALTER TABLE sales_report_details
    ALTER COLUMN staff_count TYPE INTEGER
    USING staff_count::INTEGER;

ALTER TABLE user_point_adjustments
    ALTER COLUMN points_delta TYPE INTEGER
    USING points_delta::INTEGER;
