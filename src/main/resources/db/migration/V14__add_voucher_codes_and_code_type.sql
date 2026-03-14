-- V14: Add voucher_codes table and code_type field for unique-code-per-customer model

ALTER TABLE vouchers
    ADD COLUMN IF NOT EXISTS code_type VARCHAR(10) NOT NULL DEFAULT 'SHARED';

CREATE TABLE IF NOT EXISTS voucher_codes (
    id          BIGSERIAL PRIMARY KEY,
    voucher_id  BIGINT        NOT NULL REFERENCES vouchers(id) ON DELETE CASCADE,
    code        VARCHAR(50)   NOT NULL UNIQUE,
    customer_id BIGINT        REFERENCES customers(id) ON DELETE SET NULL,
    used        BOOLEAN       NOT NULL DEFAULT false,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_voucher_codes_voucher_id   ON voucher_codes(voucher_id);
CREATE INDEX IF NOT EXISTS idx_voucher_codes_customer_id  ON voucher_codes(customer_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_voucher_codes_voucher_customer
    ON voucher_codes(voucher_id, customer_id)
    WHERE customer_id IS NOT NULL;
