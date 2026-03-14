CREATE TABLE IF NOT EXISTS email_verification_otps (
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    otp_hash       VARCHAR(255) NOT NULL,
    expires_at     TIMESTAMPTZ  NOT NULL,
    attempts       INT          NOT NULL DEFAULT 0,
    used           BOOLEAN      NOT NULL DEFAULT false,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_evo_user_id ON email_verification_otps (user_id);
