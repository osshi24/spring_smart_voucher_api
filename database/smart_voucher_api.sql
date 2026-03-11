-- =============================================
-- SMART VOUCHER SYSTEM - PostgreSQL (API-focused)
-- Hệ thống phân phối voucher, tích hợp với hệ thống bên ngoài
-- 9 bảng
-- =============================================

-- =============================================
-- ENUM TYPES
-- =============================================

CREATE TYPE user_role AS ENUM ('ADMIN', 'STAFF');
CREATE TYPE campaign_status AS ENUM ('DRAFT', 'ACTIVE', 'PAUSED', 'ENDED');
CREATE TYPE discount_type AS ENUM ('PERCENTAGE', 'FIXED_AMOUNT');
CREATE TYPE voucher_status AS ENUM ('ACTIVE', 'INACTIVE', 'EXPIRED');
CREATE TYPE distribution_channel AS ENUM ('EMAIL', 'SMS');
CREATE TYPE distribution_status AS ENUM ('PENDING', 'SENT', 'FAILED');

-- =============================================
-- 1. USERS - Quản trị viên hệ thống voucher
-- =============================================

CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name     VARCHAR(100) NOT NULL,
    email         VARCHAR(100) UNIQUE,
    phone         VARCHAR(20),
    role          user_role NOT NULL DEFAULT 'STAFF',
    is_active     BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- =============================================
-- 2. CUSTOMERS - Khách hàng nhận/sử dụng voucher
-- =============================================

CREATE TABLE customers (
    id          BIGSERIAL PRIMARY KEY,
    external_id VARCHAR(100) UNIQUE,
    full_name   VARCHAR(100) NOT NULL,
    email       VARCHAR(100) UNIQUE,
    phone       VARCHAR(20) UNIQUE,
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON COLUMN customers.external_id IS 'ID khach hang tu he thong ben ngoai (POS, e-commerce)';

-- =============================================
-- 3. CAMPAIGNS - Chiến dịch khuyến mãi
-- =============================================

CREATE TABLE campaigns (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(200) NOT NULL,
    description TEXT,
    budget      DECIMAL(15,2),
    start_date  TIMESTAMPTZ NOT NULL,
    end_date    TIMESTAMPTZ NOT NULL,
    status      campaign_status NOT NULL DEFAULT 'DRAFT',
    created_by  BIGINT NOT NULL REFERENCES users(id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CHECK (end_date > start_date)
);

COMMENT ON TABLE campaigns IS 'Chien dich khuyen mai, nhom cac voucher lai de bao cao hieu qua';
COMMENT ON COLUMN campaigns.budget IS 'Ngan sach du kien cho chien dich, dung de so sanh voi thuc chi tren dashboard';

CREATE INDEX idx_campaigns_status ON campaigns(status);
CREATE INDEX idx_campaigns_dates ON campaigns(start_date, end_date);

-- =============================================
-- 4. VOUCHERS - Bảng CORE
-- =============================================

CREATE TABLE vouchers (
    id                     BIGSERIAL PRIMARY KEY,
    code                   VARCHAR(50) NOT NULL UNIQUE,
    campaign_id            BIGINT REFERENCES campaigns(id) ON DELETE SET NULL,
    description            TEXT,

    -- Loại & giá trị giảm
    discount_type          discount_type NOT NULL,
    discount_value         DECIMAL(15,2) NOT NULL CHECK (discount_value > 0),
    max_discount_amount    DECIMAL(15,2),

    -- Điều kiện áp dụng
    min_order_value        DECIMAL(15,2) NOT NULL DEFAULT 0,
    applicable_products    JSONB DEFAULT '[]',
    applicable_categories  JSONB DEFAULT '[]',
    applicable_branches    JSONB DEFAULT '[]',

    -- Giới hạn sử dụng
    max_usage_total        INT,
    max_usage_per_customer INT,
    current_usage_count    INT NOT NULL DEFAULT 0,

    -- Phạm vi & thời gian
    is_public              BOOLEAN NOT NULL DEFAULT TRUE,
    valid_from             TIMESTAMPTZ NOT NULL,
    valid_until            TIMESTAMPTZ NOT NULL,
    status                 voucher_status NOT NULL DEFAULT 'ACTIVE',

    -- Metadata
    created_by             BIGINT NOT NULL REFERENCES users(id),
    created_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CHECK (valid_until > valid_from),
    CHECK (
        (discount_type = 'PERCENTAGE' AND discount_value <= 100)
        OR discount_type = 'FIXED_AMOUNT'
    )
);

COMMENT ON COLUMN vouchers.applicable_products IS 'Mang ID san pham tu he thong ngoai. [] = ap dung tat ca. VD: ["SP001","SP002"]';
COMMENT ON COLUMN vouchers.applicable_categories IS 'Mang ID nganh hang tu he thong ngoai. [] = ap dung tat ca. VD: ["CAT01"]';
COMMENT ON COLUMN vouchers.applicable_branches IS 'Mang ID chi nhanh tu he thong ngoai. [] = ap dung tat ca. VD: ["BR01","BR02"]';

CREATE INDEX idx_vouchers_code ON vouchers(code);
CREATE INDEX idx_vouchers_status ON vouchers(status);
CREATE INDEX idx_vouchers_validity ON vouchers(valid_from, valid_until);
CREATE INDEX idx_vouchers_campaign ON vouchers(campaign_id);

-- =============================================
-- 5. VOUCHER_CUSTOMERS - Gán voucher private cho KH
-- =============================================

CREATE TABLE voucher_customers (
    id          BIGSERIAL PRIMARY KEY,
    voucher_id  BIGINT NOT NULL REFERENCES vouchers(id) ON DELETE CASCADE,
    customer_id BIGINT NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(voucher_id, customer_id)
);

COMMENT ON TABLE voucher_customers IS 'Khi vouchers.is_public = FALSE, chi KH trong bang nay duoc dung';

-- =============================================
-- 6. VOUCHER_DISTRIBUTIONS - Phân phối qua Email/SMS
-- =============================================

CREATE TABLE voucher_distributions (
    id            BIGSERIAL PRIMARY KEY,
    voucher_id    BIGINT NOT NULL REFERENCES vouchers(id) ON DELETE CASCADE,
    customer_id   BIGINT NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    channel       distribution_channel NOT NULL,
    status        distribution_status NOT NULL DEFAULT 'PENDING',
    sent_at       TIMESTAMPTZ,
    error_message TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_distributions_voucher ON voucher_distributions(voucher_id);
CREATE INDEX idx_distributions_status ON voucher_distributions(status);

-- =============================================
-- 7. VOUCHER_USAGES - Lịch sử sử dụng
-- =============================================

CREATE TABLE voucher_usages (
    id                 BIGSERIAL PRIMARY KEY,
    voucher_id         BIGINT NOT NULL REFERENCES vouchers(id),
    customer_id        BIGINT NOT NULL REFERENCES customers(id),
    external_order_id  VARCHAR(100) NOT NULL,
    external_branch_id VARCHAR(100),
    discount_amount    DECIMAL(15,2) NOT NULL CHECK (discount_amount >= 0),
    order_total        DECIMAL(15,2) NOT NULL CHECK (order_total >= 0),
    used_at            TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON COLUMN voucher_usages.external_order_id IS 'Ma don hang tu he thong ngoai (POS, e-commerce)';
COMMENT ON COLUMN voucher_usages.external_branch_id IS 'Ma chi nhanh tu he thong ngoai';
COMMENT ON COLUMN voucher_usages.order_total IS 'Tong gia tri don hang (tu he thong ngoai gui sang)';

CREATE INDEX idx_usages_voucher ON voucher_usages(voucher_id);
CREATE INDEX idx_usages_customer ON voucher_usages(customer_id);
CREATE INDEX idx_usages_used_at ON voucher_usages(used_at);
CREATE INDEX idx_usages_external_order ON voucher_usages(external_order_id);
CREATE UNIQUE INDEX idx_usages_unique_order ON voucher_usages(voucher_id, external_order_id);

-- =============================================
-- 8. API_KEYS - Xác thực hệ thống tích hợp
-- =============================================

CREATE TABLE api_keys (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    key_hash    VARCHAR(255) NOT NULL UNIQUE,
    system_name VARCHAR(100) NOT NULL,
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    created_by  BIGINT NOT NULL REFERENCES users(id),
    expires_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE api_keys IS 'API key cho cac he thong ben ngoai tich hop (POS, e-commerce, mobile app)';
COMMENT ON COLUMN api_keys.system_name IS 'Ten he thong tich hop. VD: POS_QUAN1, ECOMMERCE_WEB, MOBILE_APP';

-- =============================================
-- 9. API_REQUEST_LOGS - Log request từ hệ thống ngoài
-- =============================================

CREATE TABLE api_request_logs (
    id              BIGSERIAL PRIMARY KEY,
    api_key_id      BIGINT REFERENCES api_keys(id),
    endpoint        VARCHAR(200) NOT NULL,
    method          VARCHAR(10) NOT NULL,
    request_body    JSONB,
    response_status INT,
    response_body   JSONB,
    ip_address      VARCHAR(45),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE api_request_logs IS 'Log tat ca request tu he thong ngoai, phuc vu debug va phat hien gian lan';

CREATE INDEX idx_api_logs_key ON api_request_logs(api_key_id);
CREATE INDEX idx_api_logs_endpoint ON api_request_logs(endpoint);
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

CREATE TRIGGER trg_users_updated_at BEFORE UPDATE ON users FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER trg_customers_updated_at BEFORE UPDATE ON customers FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER trg_campaigns_updated_at BEFORE UPDATE ON campaigns FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER trg_vouchers_updated_at BEFORE UPDATE ON vouchers FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Increment usage count (dùng row-level lock để tránh race condition)
CREATE OR REPLACE FUNCTION increment_voucher_usage()
RETURNS TRIGGER AS $$
DECLARE
    v_voucher vouchers%ROWTYPE;
    v_customer_usage_count INT;
BEGIN
    -- Lock row để tránh race condition khi nhiều request đồng thời
    SELECT * INTO v_voucher FROM vouchers WHERE id = NEW.voucher_id FOR UPDATE;

    -- Check max_usage_total
    IF v_voucher.max_usage_total IS NOT NULL
       AND v_voucher.current_usage_count >= v_voucher.max_usage_total THEN
        RAISE EXCEPTION 'Voucher % da het luot su dung (max: %)', v_voucher.code, v_voucher.max_usage_total;
    END IF;

    -- Check max_usage_per_customer
    IF v_voucher.max_usage_per_customer IS NOT NULL THEN
        SELECT COUNT(*) INTO v_customer_usage_count
        FROM voucher_usages
        WHERE voucher_id = NEW.voucher_id AND customer_id = NEW.customer_id;

        IF v_customer_usage_count >= v_voucher.max_usage_per_customer THEN
            RAISE EXCEPTION 'Khach hang da su dung voucher % het so lan cho phep (max: %)',
                v_voucher.code, v_voucher.max_usage_per_customer;
        END IF;
    END IF;

    -- Increment
    UPDATE vouchers
    SET current_usage_count = current_usage_count + 1
    WHERE id = NEW.voucher_id;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_voucher_usage_increment
    BEFORE INSERT ON voucher_usages FOR EACH ROW EXECUTE FUNCTION increment_voucher_usage();

-- =============================================
-- SAMPLE DATA
-- =============================================

INSERT INTO users (username, password_hash, full_name, email, role) VALUES
('admin01', '$2b$12$placeholder_hash_admin01', 'Nguyen Van Admin', 'admin@voucher.vn', 'ADMIN'),
('staff01', '$2b$12$placeholder_hash_staff01', 'Tran Thi Nhan Vien', 'staff01@voucher.vn', 'STAFF');

INSERT INTO customers (external_id, full_name, email, phone) VALUES
('CUS-001', 'Nguyen Thi Lan', 'lan.nguyen@gmail.com', '0901234567'),
('CUS-002', 'Tran Van Minh', 'minh.tran@gmail.com', '0912345678'),
('CUS-003', 'Le Hoang Nam', 'nam.le@gmail.com', '0923456789');

INSERT INTO campaigns (name, description, budget, start_date, end_date, status, created_by) VALUES
('Khuyen mai He 2026', 'Chien dich khuyen mai mua he, ap dung toan he thong', 50000000.00, '2026-06-01', '2026-08-31', 'DRAFT', 1),
('Flash Sale thang 3', 'Flash sale ngan han thang 3/2026', 10000000.00, '2026-03-15', '2026-03-17', 'ACTIVE', 1);

INSERT INTO vouchers (code, campaign_id, description, discount_type, discount_value, max_discount_amount, min_order_value, applicable_products, applicable_categories, applicable_branches, max_usage_total, max_usage_per_customer, is_public, valid_from, valid_until, status, created_by) VALUES
('SUMMER2026', 1, 'Giam 10% toi da 500K cho tat ca san pham', 'PERCENTAGE', 10.00, 500000.00, 200000.00, '[]', '[]', '[]', 1000, 3, TRUE, '2026-06-01', '2026-08-31', 'ACTIVE', 1),
('FLASH50K', 2, 'Giam 50K cho nganh Dien tu tai Q1 va Q3', 'FIXED_AMOUNT', 50000.00, NULL, 100000.00, '[]', '["CAT-DIENTU"]', '["BR-QUAN1","BR-QUAN3"]', 500, 1, TRUE, '2026-03-15', '2026-03-17', 'ACTIVE', 1),
('VIP100K', 2, 'Giam 100K danh rieng cho KH VIP', 'FIXED_AMOUNT', 100000.00, NULL, 500000.00, '[]', '[]', '[]', 100, 1, FALSE, '2026-03-15', '2026-03-17', 'ACTIVE', 1);

INSERT INTO voucher_customers (voucher_id, customer_id) VALUES (3, 1), (3, 2);

INSERT INTO voucher_distributions (voucher_id, customer_id, channel, status, sent_at) VALUES
(3, 1, 'EMAIL', 'SENT', '2026-03-14 10:00:00+07'),
(3, 2, 'SMS', 'SENT', '2026-03-14 10:05:00+07');

INSERT INTO api_keys (name, key_hash, system_name, created_by) VALUES
('POS Chi nhanh Quan 1', '$2b$12$placeholder_hash_key01', 'POS_QUAN1', 1),
('Website E-commerce', '$2b$12$placeholder_hash_key02', 'ECOMMERCE_WEB', 1),
('Mobile App', '$2b$12$placeholder_hash_key03', 'MOBILE_APP', 1);
