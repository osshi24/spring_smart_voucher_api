-- V17: Add merchant_profiles table for business information

CREATE TABLE IF NOT EXISTS merchant_profiles (
    id            BIGSERIAL     PRIMARY KEY,
    user_id       BIGINT        NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    business_name VARCHAR(200),
    business_type VARCHAR(50),
    address       TEXT,
    logo_url      VARCHAR(500),
    tax_code      VARCHAR(20),
    max_api_keys  INT           NOT NULL DEFAULT 10,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_merchant_profiles_user_id ON merchant_profiles(user_id);
