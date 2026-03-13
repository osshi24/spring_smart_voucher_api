-- Phase 2: RBAC, Auth, Rate Limiting foundations

-- ===== Extend users table =====
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN IF NOT EXISTS email_verified BOOLEAN NOT NULL DEFAULT false;

-- ===== Extend api_keys table =====
ALTER TABLE api_keys
    ADD COLUMN IF NOT EXISTS rate_limit_per_minute INTEGER NULL;

-- ===== Permissions table =====
CREATE TABLE IF NOT EXISTS permissions (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(255),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ===== Role-Permission mapping =====
CREATE TABLE IF NOT EXISTS role_permissions (
    role          VARCHAR(50) NOT NULL,
    permission_id BIGINT      NOT NULL REFERENCES permissions (id) ON DELETE CASCADE,
    PRIMARY KEY (role, permission_id)
);

-- ===== Email verification tokens =====
CREATE TABLE IF NOT EXISTS email_verification_tokens (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token      VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ  NOT NULL,
    used       BOOLEAN      NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ===== Password reset tokens =====
CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token      VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ  NOT NULL,
    used       BOOLEAN      NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ===== Seed permissions =====
INSERT INTO permissions (name, description) VALUES
    ('VOUCHER_CREATE',          'Create new vouchers'),
    ('VOUCHER_UPDATE',          'Update existing vouchers'),
    ('VOUCHER_DELETE',          'Delete vouchers'),
    ('VOUCHER_READ',            'Read/list vouchers'),
    ('CAMPAIGN_CREATE',         'Create new campaigns'),
    ('CAMPAIGN_UPDATE',         'Update existing campaigns'),
    ('CAMPAIGN_DELETE',         'Delete campaigns'),
    ('CAMPAIGN_READ',           'Read/list campaigns'),
    ('CUSTOMER_CREATE',         'Create new customers'),
    ('CUSTOMER_UPDATE',         'Update existing customers'),
    ('CUSTOMER_DELETE',         'Delete customers'),
    ('CUSTOMER_READ',           'Read/list customers'),
    ('APIKEY_CREATE',           'Create API keys'),
    ('APIKEY_DEACTIVATE',       'Deactivate API keys'),
    ('DISTRIBUTION_CREATE',     'Create distributions'),
    ('DISTRIBUTION_READ',       'Read/list distributions'),
    ('DASHBOARD_READ',          'View dashboard statistics'),
    ('USER_APPROVE',            'Approve pending user accounts'),
    ('USER_REJECT',             'Reject pending user accounts'),
    ('USER_READ',               'Read/list user accounts'),
    ('ROLE_PERMISSION_MANAGE',  'Assign/revoke role permissions'),
    ('REQUEST_LOG_READ',        'Read API request logs')
ON CONFLICT (name) DO NOTHING;

-- ===== ADMIN: all permissions =====
INSERT INTO role_permissions (role, permission_id)
SELECT 'ADMIN', id FROM permissions
ON CONFLICT DO NOTHING;

-- ===== STAFF: operational permissions =====
INSERT INTO role_permissions (role, permission_id)
SELECT 'STAFF', id FROM permissions
WHERE name IN (
    'VOUCHER_CREATE', 'VOUCHER_UPDATE', 'VOUCHER_READ',
    'CAMPAIGN_CREATE', 'CAMPAIGN_UPDATE', 'CAMPAIGN_READ',
    'CUSTOMER_CREATE', 'CUSTOMER_UPDATE', 'CUSTOMER_READ',
    'APIKEY_CREATE',
    'DISTRIBUTION_CREATE', 'DISTRIBUTION_READ',
    'DASHBOARD_READ'
)
ON CONFLICT DO NOTHING;

-- ===== USER: read-only permissions =====
INSERT INTO role_permissions (role, permission_id)
SELECT 'USER', id FROM permissions
WHERE name IN (
    'VOUCHER_READ',
    'CAMPAIGN_READ',
    'CUSTOMER_READ'
)
ON CONFLICT DO NOTHING;

-- ===== Backfill existing users =====
UPDATE users SET status = 'ACTIVE', email_verified = true WHERE status IS NULL OR status = '';
