CREATE TABLE IF NOT EXISTS password_reset_otps (
    id                       BIGSERIAL PRIMARY KEY,
    user_id                  BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    otp_hash                 VARCHAR(255) NOT NULL,
    reset_token              VARCHAR(255) UNIQUE,
    otp_expires_at           TIMESTAMPTZ  NOT NULL,
    reset_token_expires_at   TIMESTAMPTZ,
    attempts                 INT          NOT NULL DEFAULT 0,
    used                     BOOLEAN      NOT NULL DEFAULT false,
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_prot_user_id ON password_reset_otps (user_id);
CREATE INDEX IF NOT EXISTS idx_prot_reset_token ON password_reset_otps (reset_token);
