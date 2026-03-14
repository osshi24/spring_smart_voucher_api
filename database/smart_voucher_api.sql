-- =============================================
-- SMART VOUCHER SYSTEM - PostgreSQL (API-focused)
-- Hệ thống phân phối voucher, tích hợp với hệ thống bên ngoài
-- 13 bảng
-- =============================================

-- =============================================
-- ENUM TYPES (dùng VARCHAR trong JPA thực tế)
-- =============================================

CREATE TYPE user_role             AS ENUM ('ADMIN', 'STAFF', 'USER');
CREATE TYPE user_status           AS ENUM ('ACTIVE', 'PENDING', 'REJECTED', 'SUSPENDED');
CREATE TYPE campaign_status       AS ENUM ('DRAFT', 'ACTIVE', 'PAUSED', 'ENDED');
CREATE TYPE discount_type         AS ENUM ('PERCENTAGE', 'FIXED_AMOUNT');
CREATE TYPE voucher_status        AS ENUM ('ACTIVE', 'INACTIVE', 'EXPIRED');
CREATE TYPE distribution_channel  AS ENUM ('EMAIL', 'SMS');
CREATE TYPE distribution_status   AS ENUM ('PENDING', 'SENT', 'FAILED', 'CANCELLED');

-- =============================================
-- 1. USERS - Quản trị viên hệ thống voucher
-- =============================================

CREATE TABLE users (
    id             BIGSERIAL PRIMARY KEY,
    username       VARCHAR(50)  NOT NULL UNIQUE,
    password_hash  VARCHAR(255) NOT NULL,
    full_name      VARCHAR(100) NOT NULL,
    email          VARCHAR(100) UNIQUE,
    phone          VARCHAR(20),
    role           VARCHAR(50)  NOT NULL DEFAULT 'STAFF',
    is_active      BOOLEAN      NOT NULL DEFAULT TRUE,
    status         VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    email_verified BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

COMMENT ON COLUMN users.role   IS 'ADMIN | STAFF | USER';
COMMENT ON COLUMN users.status IS 'ACTIVE | PENDING | REJECTED | SUSPENDED';

-- =============================================
-- 2. PERMISSIONS - Danh sách quyền
-- =============================================

CREATE TABLE permissions (
    id          BIGSERIAL    PRIMARY KEY,
    name        VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(255),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- =============================================
-- 3. ROLE_PERMISSIONS - Phân quyền theo role
-- =============================================

CREATE TABLE role_permissions (
    role          VARCHAR(50) NOT NULL,
    permission_id BIGINT      NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
    PRIMARY KEY (role, permission_id)
);

COMMENT ON TABLE role_permissions IS 'Moi role (ADMIN/STAFF/USER) co bo quyen rieng';

-- =============================================
-- 4. EMAIL_VERIFICATION_TOKENS
-- =============================================

CREATE TABLE email_verification_tokens (
    id         BIGSERIAL    PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token      VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ  NOT NULL,
    used       BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE email_verification_tokens IS 'Token xac thuc email, het han sau 24 gio, chi dung 1 lan';

-- =============================================
-- 5. PASSWORD_RESET_TOKENS
-- =============================================

CREATE TABLE password_reset_tokens (
    id         BIGSERIAL    PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token      VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ  NOT NULL,
    used       BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE password_reset_tokens IS 'Token dat lai mat khau, het han sau 1 gio, chi dung 1 lan';

-- =============================================
-- 6. CUSTOMERS - Khách hàng nhận/sử dụng voucher
-- =============================================

CREATE TABLE customers (
    id          BIGSERIAL    PRIMARY KEY,
    external_id VARCHAR(100) UNIQUE,
    full_name   VARCHAR(100) NOT NULL,
    email       VARCHAR(100) UNIQUE,
    phone       VARCHAR(20)  UNIQUE,
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

COMMENT ON COLUMN customers.external_id IS 'ID khach hang tu he thong ben ngoai (POS, e-commerce)';

-- =============================================
-- 7. CAMPAIGNS - Chiến dịch khuyến mãi
-- =============================================

CREATE TABLE campaigns (
    id          BIGSERIAL    PRIMARY KEY,
    name        VARCHAR(200) NOT NULL,
    description TEXT,
    budget      DECIMAL(15,2),
    start_date  TIMESTAMPTZ  NOT NULL,
    end_date    TIMESTAMPTZ  NOT NULL,
    status      VARCHAR(50)  NOT NULL DEFAULT 'DRAFT',
    created_by  BIGINT       NOT NULL REFERENCES users(id),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CHECK (end_date > start_date)
);

COMMENT ON TABLE  campaigns        IS 'Chien dich khuyen mai, nhom cac voucher lai de bao cao hieu qua';
COMMENT ON COLUMN campaigns.budget IS 'Ngan sach du kien cho chien dich, dung de so sanh voi thuc chi tren dashboard';

CREATE INDEX idx_campaigns_status ON campaigns(status);
CREATE INDEX idx_campaigns_dates  ON campaigns(start_date, end_date);

-- =============================================
-- 8. VOUCHERS - Bảng CORE
-- =============================================

CREATE TABLE vouchers (
    id                     BIGSERIAL    PRIMARY KEY,
    code                   VARCHAR(50)  NOT NULL UNIQUE,
    campaign_id            BIGINT       REFERENCES campaigns(id) ON DELETE SET NULL,
    description            TEXT,

    -- Loại & giá trị giảm
    discount_type          VARCHAR(50)  NOT NULL,
    discount_value         DECIMAL(15,2) NOT NULL CHECK (discount_value > 0),
    max_discount_amount    DECIMAL(15,2),

    -- Điều kiện áp dụng
    min_order_value        DECIMAL(15,2) NOT NULL DEFAULT 0,
    applicable_products    JSONB         DEFAULT '[]',
    applicable_categories  JSONB         DEFAULT '[]',
    applicable_branches    JSONB         DEFAULT '[]',

    -- Giới hạn sử dụng
    max_usage_total        INT,
    max_usage_per_customer INT,
    current_usage_count    INT          NOT NULL DEFAULT 0,

    -- Phạm vi & thời gian
    is_public              BOOLEAN      NOT NULL DEFAULT TRUE,
    valid_from             TIMESTAMPTZ  NOT NULL,
    valid_until            TIMESTAMPTZ  NOT NULL,
    status                 VARCHAR(50)  NOT NULL DEFAULT 'ACTIVE',

    -- Metadata
    created_by             BIGINT       NOT NULL REFERENCES users(id),
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CHECK (valid_until > valid_from),
    CHECK (
        (discount_type = 'PERCENTAGE' AND discount_value <= 100)
        OR discount_type = 'FIXED_AMOUNT'
    )
);

COMMENT ON COLUMN vouchers.applicable_products   IS 'Mang ID san pham tu he thong ngoai. [] = ap dung tat ca. VD: ["SP001","SP002"]';
COMMENT ON COLUMN vouchers.applicable_categories IS 'Mang ID nganh hang tu he thong ngoai. [] = ap dung tat ca. VD: ["CAT01"]';
COMMENT ON COLUMN vouchers.applicable_branches   IS 'Mang ID chi nhanh tu he thong ngoai. [] = ap dung tat ca. VD: ["BR01","BR02"]';

CREATE INDEX idx_vouchers_code     ON vouchers(code);
CREATE INDEX idx_vouchers_status   ON vouchers(status);
CREATE INDEX idx_vouchers_validity ON vouchers(valid_from, valid_until);
CREATE INDEX idx_vouchers_campaign ON vouchers(campaign_id);

-- =============================================
-- 9. VOUCHER_CUSTOMERS - Gán voucher private cho KH
-- =============================================

CREATE TABLE voucher_customers (
    id          BIGSERIAL   PRIMARY KEY,
    voucher_id  BIGINT      NOT NULL REFERENCES vouchers(id)   ON DELETE CASCADE,
    customer_id BIGINT      NOT NULL REFERENCES customers(id)  ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(voucher_id, customer_id)
);

COMMENT ON TABLE voucher_customers IS 'Khi vouchers.is_public = FALSE, chi KH trong bang nay duoc dung';

-- =============================================
-- 10. VOUCHER_DISTRIBUTIONS - Phân phối qua Email/SMS
-- =============================================

CREATE TABLE voucher_distributions (
    id            BIGSERIAL   PRIMARY KEY,
    voucher_id    BIGINT      NOT NULL REFERENCES vouchers(id)  ON DELETE CASCADE,
    customer_id   BIGINT      NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    channel       VARCHAR(50) NOT NULL,
    status        VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    sent_at       TIMESTAMPTZ,
    error_message TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON COLUMN voucher_distributions.channel IS 'EMAIL | SMS';
COMMENT ON COLUMN voucher_distributions.status  IS 'PENDING | SENT | FAILED | CANCELLED';

CREATE INDEX idx_distributions_voucher ON voucher_distributions(voucher_id);
CREATE INDEX idx_distributions_status  ON voucher_distributions(status);

-- =============================================
-- 11. VOUCHER_USAGES - Lịch sử sử dụng
-- =============================================

CREATE TABLE voucher_usages (
    id                 BIGSERIAL     PRIMARY KEY,
    voucher_id         BIGINT        NOT NULL REFERENCES vouchers(id),
    customer_id        BIGINT        NOT NULL REFERENCES customers(id),
    external_order_id  VARCHAR(100)  NOT NULL,
    external_branch_id VARCHAR(100),
    discount_amount    DECIMAL(15,2) NOT NULL CHECK (discount_amount >= 0),
    order_total        DECIMAL(15,2) NOT NULL CHECK (order_total >= 0),
    used_at            TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

COMMENT ON COLUMN voucher_usages.external_order_id  IS 'Ma don hang tu he thong ngoai (POS, e-commerce)';
COMMENT ON COLUMN voucher_usages.external_branch_id IS 'Ma chi nhanh tu he thong ngoai';
COMMENT ON COLUMN voucher_usages.order_total        IS 'Tong gia tri don hang (tu he thong ngoai gui sang)';

CREATE INDEX idx_usages_voucher        ON voucher_usages(voucher_id);
CREATE INDEX idx_usages_customer       ON voucher_usages(customer_id);
CREATE INDEX idx_usages_used_at        ON voucher_usages(used_at);
CREATE INDEX idx_usages_external_order ON voucher_usages(external_order_id);
CREATE UNIQUE INDEX idx_usages_unique_order ON voucher_usages(voucher_id, external_order_id);

-- =============================================
-- 12. API_KEYS - Xác thực hệ thống tích hợp
-- =============================================

CREATE TABLE api_keys (
    id                   BIGSERIAL    PRIMARY KEY,
    name                 VARCHAR(100) NOT NULL,
    key_hash             VARCHAR(255) NOT NULL UNIQUE,
    system_name          VARCHAR(100) NOT NULL,
    is_active            BOOLEAN      NOT NULL DEFAULT TRUE,
    created_by           BIGINT       NOT NULL REFERENCES users(id),
    rate_limit_per_minute INT,
    rate_limit_per_day    INT,
    expires_at           TIMESTAMPTZ,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  api_keys                         IS 'API key cho cac he thong ben ngoai tich hop (POS, e-commerce, mobile app)';
COMMENT ON COLUMN api_keys.system_name             IS 'Ten he thong tich hop. VD: POS_QUAN1, ECOMMERCE_WEB, MOBILE_APP';
COMMENT ON COLUMN api_keys.rate_limit_per_minute   IS 'So request toi da/phut. NULL = khong gioi han';
COMMENT ON COLUMN api_keys.rate_limit_per_day      IS 'So request toi da/ngay. NULL = khong gioi han';

-- =============================================
-- 13. API_REQUEST_LOGS - Log request từ hệ thống ngoài
-- =============================================

CREATE TABLE api_request_logs (
    id               BIGSERIAL    PRIMARY KEY,
    api_key_id       BIGINT       REFERENCES api_keys(id),
    endpoint         VARCHAR(200) NOT NULL,
    method           VARCHAR(10)  NOT NULL,
    request_body     JSONB,
    response_status  INT,
    response_body    JSONB,
    response_time_ms BIGINT,
    ip_address       VARCHAR(45),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  api_request_logs                  IS 'Log tat ca request tu he thong ngoai, phuc vu debug va phat hien gian lan';
COMMENT ON COLUMN api_request_logs.response_time_ms IS 'Thoi gian xu ly request (milliseconds)';

CREATE INDEX idx_api_logs_key        ON api_request_logs(api_key_id);
CREATE INDEX idx_api_logs_endpoint   ON api_request_logs(endpoint);
CREATE INDEX idx_api_logs_created_at ON api_request_logs(created_at);

-- =============================================
-- TRIGGERS
-- =============================================

-- Auto update updated_at
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_users_updated_at     BEFORE UPDATE ON users     FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER trg_customers_updated_at BEFORE UPDATE ON customers FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER trg_campaigns_updated_at BEFORE UPDATE ON campaigns FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Increment usage count (dùng row-level lock để tránh race condition)
CREATE OR REPLACE FUNCTION increment_voucher_usage()
RETURNS TRIGGER AS $$
DECLARE
    v_voucher              vouchers%ROWTYPE;
    v_customer_usage_count INT;
BEGIN
    SELECT * INTO v_voucher FROM vouchers WHERE id = NEW.voucher_id FOR UPDATE;

    IF v_voucher.max_usage_total IS NOT NULL
       AND v_voucher.current_usage_count >= v_voucher.max_usage_total THEN
        RAISE EXCEPTION 'Voucher % da het luot su dung (max: %)', v_voucher.code, v_voucher.max_usage_total;
    END IF;

    IF v_voucher.max_usage_per_customer IS NOT NULL THEN
        SELECT COUNT(*) INTO v_customer_usage_count
        FROM voucher_usages
        WHERE voucher_id = NEW.voucher_id AND customer_id = NEW.customer_id;

        IF v_customer_usage_count >= v_voucher.max_usage_per_customer THEN
            RAISE EXCEPTION 'Khach hang da su dung voucher % het so lan cho phep (max: %)',
                v_voucher.code, v_voucher.max_usage_per_customer;
        END IF;
    END IF;

    UPDATE vouchers SET current_usage_count = current_usage_count + 1 WHERE id = NEW.voucher_id;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_voucher_usage_increment
    BEFORE INSERT ON voucher_usages FOR EACH ROW EXECUTE FUNCTION increment_voucher_usage();

-- =============================================
-- SAMPLE DATA
-- =============================================

INSERT INTO users (username, password_hash, full_name, email, role, status, email_verified) VALUES
('admin01', '$2a$10$AWH5Yqpjd8fMViRkQ/10YuiGQZrWF7UQ9say8glR6KRnQdqApMUxK', 'Nguyen Van Admin', 'admin@voucher.vn', 'ADMIN', 'ACTIVE', TRUE),
('staff01', '$2a$10$Z1VH3.JnAQKyqmoTUYnHm.sqSRShcq4o1PNt2cyQq20LtY1.EZnpm', 'Tran Thi Nhan Vien', 'staff01@voucher.vn', 'STAFF', 'ACTIVE', TRUE);
-- admin01: password = admin123
-- staff01: password = staff123

INSERT INTO permissions (name, description) VALUES
    ('VOUCHER_CREATE',         'Create new vouchers'),
    ('VOUCHER_UPDATE',         'Update existing vouchers'),
    ('VOUCHER_DELETE',         'Delete vouchers'),
    ('VOUCHER_READ',           'Read/list vouchers'),
    ('CAMPAIGN_CREATE',        'Create new campaigns'),
    ('CAMPAIGN_UPDATE',        'Update existing campaigns'),
    ('CAMPAIGN_DELETE',        'Delete campaigns'),
    ('CAMPAIGN_READ',          'Read/list campaigns'),
    ('CUSTOMER_CREATE',        'Create new customers'),
    ('CUSTOMER_UPDATE',        'Update existing customers'),
    ('CUSTOMER_DELETE',        'Delete customers'),
    ('CUSTOMER_READ',          'Read/list customers'),
    ('APIKEY_CREATE',          'Create API keys'),
    ('APIKEY_DEACTIVATE',      'Deactivate API keys'),
    ('DISTRIBUTION_CREATE',    'Create distributions'),
    ('DISTRIBUTION_READ',      'Read/list distributions'),
    ('DASHBOARD_READ',         'View dashboard statistics'),
    ('USER_APPROVE',           'Approve pending user accounts'),
    ('USER_REJECT',            'Reject pending user accounts'),
    ('USER_READ',              'Read/list user accounts'),
    ('USER_MANAGE',            'Manage user accounts (update details, change role)'),
    ('ROLE_PERMISSION_MANAGE', 'Assign/revoke role permissions'),
    ('REQUEST_LOG_READ',       'Read API request logs');

-- ADMIN: all permissions
INSERT INTO role_permissions (role, permission_id)
SELECT 'ADMIN', id FROM permissions;

-- STAFF: operational permissions
INSERT INTO role_permissions (role, permission_id)
SELECT 'STAFF', id FROM permissions
WHERE name IN (
    'VOUCHER_CREATE', 'VOUCHER_UPDATE', 'VOUCHER_READ',
    'CAMPAIGN_CREATE', 'CAMPAIGN_UPDATE', 'CAMPAIGN_READ',
    'CUSTOMER_CREATE', 'CUSTOMER_UPDATE', 'CUSTOMER_READ',
    'APIKEY_CREATE',
    'DISTRIBUTION_CREATE', 'DISTRIBUTION_READ',
    'DASHBOARD_READ'
);

-- USER: read-only
INSERT INTO role_permissions (role, permission_id)
SELECT 'USER', id FROM permissions
WHERE name IN ('VOUCHER_READ', 'CAMPAIGN_READ', 'CUSTOMER_READ');

INSERT INTO customers (external_id, full_name, email, phone) VALUES
('CUS-001', 'Nguyen Thi Lan', 'lan.nguyen@gmail.com', '0901234567'),
('CUS-002', 'Tran Van Minh',  'minh.tran@gmail.com',  '0912345678'),
('CUS-003', 'Le Hoang Nam',   'nam.le@gmail.com',     '0923456789');

INSERT INTO campaigns (name, description, budget, start_date, end_date, status, created_by) VALUES
('Khuyen mai He 2026',  'Chien dich khuyen mai mua he, ap dung toan he thong', 50000000.00, '2026-06-01', '2026-08-31', 'DRAFT',  1),
('Flash Sale thang 3',  'Flash sale ngan han thang 3/2026',                    10000000.00, '2026-03-15', '2026-03-17', 'ACTIVE', 1);

INSERT INTO vouchers (code, campaign_id, description, discount_type, discount_value, max_discount_amount, min_order_value, max_usage_total, max_usage_per_customer, is_public, valid_from, valid_until, status, created_by) VALUES
('SUMMER2026', 1, 'Giam 10% toi da 500K',          'PERCENTAGE',  10.00,    500000.00, 200000.00, 1000, 3, TRUE,  '2026-06-01', '2026-08-31', 'ACTIVE', 1),
('FLASH50K',   2, 'Giam 50K cho nganh Dien tu',     'FIXED_AMOUNT', 50000.00, NULL,     100000.00, 500,  1, TRUE,  '2026-03-15', '2026-03-17', 'ACTIVE', 1),
('VIP100K',    2, 'Giam 100K danh rieng cho KH VIP','FIXED_AMOUNT',100000.00, NULL,     500000.00, 100,  1, FALSE, '2026-03-15', '2026-03-17', 'ACTIVE', 1);

INSERT INTO voucher_customers (voucher_id, customer_id) VALUES (3, 1), (3, 2);

INSERT INTO voucher_distributions (voucher_id, customer_id, channel, status, sent_at) VALUES
(3, 1, 'EMAIL', 'SENT', '2026-03-14 10:00:00+07'),
(3, 2, 'SMS',   'SENT', '2026-03-14 10:05:00+07');

INSERT INTO api_keys (name, key_hash, system_name, rate_limit_per_minute, rate_limit_per_day, created_by) VALUES
('POS Chi nhanh Quan 1', '$2b$12$placeholder_hash_key01', 'POS_QUAN1',     60,  5000, 1),
('Website E-commerce',   '$2b$12$placeholder_hash_key02', 'ECOMMERCE_WEB', 100, 10000, 1),
('Mobile App',           '$2b$12$placeholder_hash_key03', 'MOBILE_APP',    60,  5000, 1);
