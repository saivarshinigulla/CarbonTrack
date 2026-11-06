ALTER TABLE goals
    ADD COLUMN baseline_start_date DATE,
    ADD COLUMN baseline_end_date DATE,
    ADD COLUMN baseline_period_days INTEGER,
    ADD COLUMN baseline_footprint_kg NUMERIC(12,4);
