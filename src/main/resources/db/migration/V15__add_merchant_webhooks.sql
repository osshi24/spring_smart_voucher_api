-- V15: Add merchant_webhooks table for real-time redemption notifications

CREATE TABLE IF NOT EXISTS merchant_webhooks (
    id                BIGSERIAL     PRIMARY KEY,
    user_id           BIGINT        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    url               VARCHAR(500)  NOT NULL,
    secret            VARCHAR(255)  NOT NULL,
    events            TEXT[]        NOT NULL DEFAULT ARRAY['voucher.redeemed'],
    is_active         BOOLEAN       NOT NULL DEFAULT true,
    failure_count     INT           NOT NULL DEFAULT 0,
    last_triggered_at TIMESTAMPTZ,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_merchant_webhooks_user_id   ON merchant_webhooks(user_id);
CREATE INDEX IF NOT EXISTS idx_merchant_webhooks_is_active ON merchant_webhooks(is_active);
