-- V18: Add failure_reason column to voucher_distributions for clearer error tracking

ALTER TABLE voucher_distributions
    ADD COLUMN IF NOT EXISTS failure_reason VARCHAR(500);
