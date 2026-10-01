ALTER TABLE stock_skus
    ADD COLUMN stock_check_day_3 INTEGER,
    ADD COLUMN stock_check_day_4 INTEGER,
    ADD COLUMN stock_check_day_5 INTEGER,
    ADD COLUMN stock_check_day_6 INTEGER,
    ADD COLUMN stock_check_day_7 INTEGER;

ALTER TABLE stock_skus
    ADD CONSTRAINT ck_stock_skus_weekday_3 CHECK (
        stock_check_day_3 IS NULL OR (
            stock_check_schedule = 'WEEKLY'
            AND stock_check_day_3 BETWEEN 1 AND 7
            AND stock_check_day_3 IS DISTINCT FROM stock_check_day
            AND stock_check_day_3 IS DISTINCT FROM stock_check_day_2
        )
    ),
    ADD CONSTRAINT ck_stock_skus_weekday_4 CHECK (
        stock_check_day_4 IS NULL OR (
            stock_check_schedule = 'WEEKLY'
            AND stock_check_day_4 BETWEEN 1 AND 7
            AND stock_check_day_4 IS DISTINCT FROM stock_check_day
            AND stock_check_day_4 IS DISTINCT FROM stock_check_day_2
            AND stock_check_day_4 IS DISTINCT FROM stock_check_day_3
        )
    ),
    ADD CONSTRAINT ck_stock_skus_weekday_5 CHECK (
        stock_check_day_5 IS NULL OR (
            stock_check_schedule = 'WEEKLY'
            AND stock_check_day_5 BETWEEN 1 AND 7
            AND stock_check_day_5 IS DISTINCT FROM stock_check_day
            AND stock_check_day_5 IS DISTINCT FROM stock_check_day_2
            AND stock_check_day_5 IS DISTINCT FROM stock_check_day_3
            AND stock_check_day_5 IS DISTINCT FROM stock_check_day_4
        )
    ),
    ADD CONSTRAINT ck_stock_skus_weekday_6 CHECK (
        stock_check_day_6 IS NULL OR (
            stock_check_schedule = 'WEEKLY'
            AND stock_check_day_6 BETWEEN 1 AND 7
            AND stock_check_day_6 IS DISTINCT FROM stock_check_day
            AND stock_check_day_6 IS DISTINCT FROM stock_check_day_2
            AND stock_check_day_6 IS DISTINCT FROM stock_check_day_3
            AND stock_check_day_6 IS DISTINCT FROM stock_check_day_4
            AND stock_check_day_6 IS DISTINCT FROM stock_check_day_5
        )
    ),
    ADD CONSTRAINT ck_stock_skus_weekday_7 CHECK (
        stock_check_day_7 IS NULL OR (
            stock_check_schedule = 'WEEKLY'
            AND stock_check_day_7 BETWEEN 1 AND 7
            AND stock_check_day_7 IS DISTINCT FROM stock_check_day
            AND stock_check_day_7 IS DISTINCT FROM stock_check_day_2
            AND stock_check_day_7 IS DISTINCT FROM stock_check_day_3
            AND stock_check_day_7 IS DISTINCT FROM stock_check_day_4
            AND stock_check_day_7 IS DISTINCT FROM stock_check_day_5
            AND stock_check_day_7 IS DISTINCT FROM stock_check_day_6
        )
    );
