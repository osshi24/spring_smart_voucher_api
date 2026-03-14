-- V19: Add preferred_channel to customers for notification preference

ALTER TABLE customers
    ADD COLUMN IF NOT EXISTS preferred_channel VARCHAR(10);
